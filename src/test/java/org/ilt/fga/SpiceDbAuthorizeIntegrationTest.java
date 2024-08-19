package org.ilt.fga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import org.ilt.fga.SpiceDbAuthorizeIntegrationTest.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestConfig.class},
    properties = {"spring.main.allow-bean-definition-overriding=true"})
public class SpiceDbAuthorizeIntegrationTest {

  @Autowired private TestService testService;

  @MockBean private PermissionsServiceGrpc.PermissionsServiceImplBase serviceImpl;

  @Test
  void testAuthorizeSuccess() {
    TestService.User user = new TestService.User("123", "456");

    doAnswer(
            invocation -> {
              StreamObserver<CheckPermissionResponse> responseObserver =
                  (StreamObserver<CheckPermissionResponse>) invocation.getArguments()[1];
              responseObserver.onNext(
                  CheckPermissionResponse.newBuilder()
                      .setPermissionship(
                          CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION)
                      .build());
              responseObserver.onCompleted();
              return null;
            })
        .when(serviceImpl)
        .checkPermission(any(CheckPermissionRequest.class), any());

    String result = testService.getProtectedResource(user);
    assertEquals("Protected resource for user: 123", result);
  }

  @Test
  void testAuthorizeDenied() {
    TestService.User user = new TestService.User("123", "456");

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

    assertThrows(UnauthorizedException.class, () -> testService.getProtectedResource(user));
  }

  @Configuration
  @EnableAspectJAutoProxy
  @ComponentScan(basePackages = "org.ilt.fga")
  static class TestConfig {
    @Bean
    public GrpcCleanupRule grpcCleanupRule() {
      return new GrpcCleanupRule();
    }

    @Bean
    public PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService(
        GrpcCleanupRule grpcCleanupRule,PermissionsServiceGrpc.PermissionsServiceImplBase serviceImpl) throws IOException {

      String serverName = InProcessServerBuilder.generateName();

      grpcCleanupRule.register(
          InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(serviceImpl)
              .build()
              .start());

      ManagedChannel channel =
          grpcCleanupRule.register(
              InProcessChannelBuilder.forName(serverName).directExecutor().build());

      return PermissionsServiceGrpc.newBlockingStub(channel)
          .withCallCredentials(new BearerToken("test"));
    }

    //    @Bean
    //    public SpiceDbAuthorizeAspect spiceDbAuthorizeAspect(
    //        PermissionsServiceBlockingStub permissionsService) {
    //      return new SpiceDbAuthorizeAspect(permissionsService);
    //    }

    @Bean
    public TestService testService() {
      return new TestService();
    }
  }

  public static class TestService {

    @SpiceDbAuthorize(permission = "account:{user.accountId}#READ@user:{user.id}")
    public String getProtectedResource(User user) {
      return "Protected resource for user: " + user.getId();
    }

    public static class User {
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
}
