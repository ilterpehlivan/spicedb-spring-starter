package org.ilt.fga;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.SubjectReference;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class FgAuthorizeAspect {

  private final PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService;

  public FgAuthorizeAspect(
      PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService) {
    this.permissionsService = permissionsService;
  }

  @Around("@annotation(org.ilt.fga.FgaAuthorize)")
  public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    FgaAuthorize annotation = method.getAnnotation(FgaAuthorize.class);

    String permission = annotation.permission();
    String subject = resolveValue(annotation.subject(), joinPoint);
    String object = annotation.object();

    CheckPermissionRequest request =
        CheckPermissionRequest.newBuilder()
            .setConsistency(Consistency.newBuilder().setFullyConsistent(true).build())
            .setResource(
                ObjectReference.newBuilder().setObjectType(object).setObjectId(subject).build())
            .setPermission(permission)
            .setSubject(
                SubjectReference.newBuilder()
                    .setObject(
                        ObjectReference.newBuilder()
                            .setObjectType("user")
                            .setObjectId(subject)
                            .build())
                    .build())
            .build();

    CheckPermissionResponse response = permissionsService.checkPermission(request);

    if (response.getPermissionship()
        != CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION) {
      throw new SecurityException("Access denied");
    }

    return joinPoint.proceed();
  }

  private String resolveValue(String expression, ProceedingJoinPoint joinPoint) {
    // This is a simple implementation. You might need to enhance this
    // to handle more complex expressions like "user.id"
    if (expression.contains(".")) {
      String[] parts = expression.split("\\.");
      Object arg = joinPoint.getArgs()[0];
      try {
        return arg.getClass().getMethod("get" + capitalize(parts[1])).invoke(arg).toString();
      } catch (Exception e) {
        throw new RuntimeException("Failed to resolve " + expression, e);
      }
    }
    return expression;
  }

  private String capitalize(String str) {
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }
}
