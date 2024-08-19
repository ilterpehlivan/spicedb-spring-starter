package org.ilt.fga;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.PermissionsServiceGrpc.PermissionsServiceBlockingStub;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SpiceDbAuthorizeAspectTest {

  private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @Mock private ProceedingJoinPoint joinPoint;

  @Mock private MethodSignature methodSignature;

  @Mock private FgaAuthorize fgaAuthorize;

  private SpiceDbAuthorizeAspect aspect;

  private PermissionsServiceGrpc.PermissionsServiceImplBase serviceImpl;

  @BeforeEach
  public void setUp() throws IOException {
    String serverName = InProcessServerBuilder.generateName();

    serviceImpl = mock(PermissionsServiceGrpc.PermissionsServiceImplBase.class);

    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serviceImpl)
            .build()
            .start());

    ManagedChannel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

    PermissionsServiceBlockingStub serviceBlockingStub =
        PermissionsServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(new BearerToken("test"));

    aspect = new SpiceDbAuthorizeAspect(serviceBlockingStub);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
  }

  @Test
  public void testAuthorizeSuccess() throws Throwable {
    Method method = TestClass.class.getMethod("testMethod", User.class);
    when(methodSignature.getMethod()).thenReturn(method);
    User user = new User("123", "456");
    when(joinPoint.getArgs()).thenReturn(new Object[]{user});

    doAnswer(invocation -> {
      var responseObserver = (io.grpc.stub.StreamObserver<CheckPermissionResponse>) invocation.getArguments()[1];
      responseObserver.onNext(CheckPermissionResponse.newBuilder()
          .setPermissionship(CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION)
          .build());
      responseObserver.onCompleted();
      return null;
    }).when(serviceImpl).checkPermission(any(CheckPermissionRequest.class), any());

    SpiceDbAuthorize fgaAuthorize = method.getAnnotation(SpiceDbAuthorize.class);
    aspect.authorize(joinPoint);

    verify(joinPoint).proceed();
  }

  @Test
  public void testAuthorizeDenied() throws Throwable {
    Method method = TestClass.class.getMethod("testMethod", User.class);
    when(methodSignature.getMethod()).thenReturn(method);
    SpiceDbAuthorize fgaAuthorize = method.getAnnotation(SpiceDbAuthorize.class);
    User user = new User("123", "456");
    when(joinPoint.getArgs()).thenReturn(new Object[]{user});
    doAnswer(
            invocation -> {
              StreamObserver<CheckPermissionResponse> responseObserver =
                  (StreamObserver<CheckPermissionResponse>) invocation.getArguments()[1];
              responseObserver.onNext(
                  CheckPermissionResponse.newBuilder()
                      .setPermissionship(
                          CheckPermissionResponse.Permissionship.PERMISSIONSHIP_NO_PERMISSION)
                      .build());
              responseObserver.onCompleted();
              return null;
            })
        .when(serviceImpl)
        .checkPermission(any(CheckPermissionRequest.class), any());

    assertThrows(UnauthorizedException.class, () -> aspect.authorize(joinPoint));
  }

  @Test
  public void testInvalidPermissionFormat() throws NoSuchMethodException {
    Method method = TestClass.class.getMethod("invalidMethod", User.class);
    when(methodSignature.getMethod()).thenReturn(method);
    SpiceDbAuthorize fgaAuthorize = method.getAnnotation(SpiceDbAuthorize.class);
    User user = new User("123", "456");
    when(joinPoint.getArgs()).thenReturn(new Object[]{user});
    assertThrows(FgaAuthorizationException.class, () -> aspect.authorize(joinPoint));
  }

  private static class TestClass {
    @SpiceDbAuthorize(permission = "account:{user.accountId}#READ@user:{user.id}")
    public void testMethod(User user) {
    }

    @SpiceDbAuthorize(permission = "invalid:permission:format")
    public void invalidMethod(User user) {
    }
  }

  private static class User {
    private final String id;
    private final String accountId;

    public User(String id, String accountId) {
      this.id = id;
      this.accountId = accountId;
    }

    public String getId() {
      return id;
    }

    public String getAccountId() {
      return accountId;
    }
  }
}
