package org.ilt.fga;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.SubjectReference;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SpiceDbAuthorizeAspect {

  public static final String PERMISSION_VALIDATION_REGEX =
      "^[\\w-]+:\\{[\\w.]+}#[\\w-]+@[\\w-]+:\\{[\\w.]+}$";
  //  private static final String PERMISSION_VALIDATION_REGEX =
  //      "^([^:#@]+):(#\\{[^}]+\\}|[^#@]+)#([^:#@]+)@([^:#@]+):(#\\{[^}]+\\}|[^#@]+)$";
  private final PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService;

  public SpiceDbAuthorizeAspect(
      PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService) {
    this.permissionsService = permissionsService;
  }

  @Around("@annotation(org.ilt.fga.SpiceDbAuthorize)")
  public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    SpiceDbAuthorize spiceDbAuthorize = method.getAnnotation(SpiceDbAuthorize.class);
    String permission = spiceDbAuthorize.permission();
    validatePermissionFormat(permission);
    Object[] args = joinPoint.getArgs();

    String[] parts = permission.split("#|@");
    if (parts.length != 3) {
      throw new UnauthorizedException("Invalid permission format");
    }

    String objectType = parts[0].split(":")[0];
    String objectId = resolveExpression(parts[0].split(":")[1], method, args);
    String action = parts[1];
    String subjectType = parts[2].split(":")[0];
    String subjectId = resolveExpression(parts[2].split(":")[1], method, args);

    CheckPermissionRequest request =
        CheckPermissionRequest.newBuilder()
            .setResource(
                ObjectReference.newBuilder().setObjectType(objectType).setObjectId(objectId))
            .setPermission(action)
            .setSubject(
                SubjectReference.newBuilder()
                    .setObject(
                        ObjectReference.newBuilder()
                            .setObjectType(subjectType)
                            .setObjectId(subjectId)))
            .build();

    CheckPermissionResponse response = permissionsService.checkPermission(request);

    if (response.getPermissionship()
        != CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION) {
      throw new UnauthorizedException("Access denied");
    }

    return joinPoint.proceed();
  }

  private void validatePermissionFormat(String permission) {
    Pattern pattern = Pattern.compile(PERMISSION_VALIDATION_REGEX);
    Matcher matcher = pattern.matcher(permission);
    if (!matcher.matches()) {
      throw new UnauthorizedException("Invalid permission format");
    }
  }

  private String resolveExpression(String expression, Method method, Object[] args) {
    if (!expression.startsWith("{") || !expression.endsWith("}")) {
      return expression;
    }
    StandardEvaluationContext context = new StandardEvaluationContext();

    Parameter[] parameters = method.getParameters();
    String[] parameterNames = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      parameterNames[i] = parameters[i].getType().getSimpleName().toLowerCase();
      context.setVariable(parameterNames[i], args[i]);
    }

    ExpressionParser parser = new SpelExpressionParser();

    try {
      expression = String.format("#%s", expression.replaceAll("\\{(.*)\\}$", "$1"));
      Object value = parser.parseExpression(expression).getValue(context);
      if (value == null) {
        throw new FgaAuthorizationException("Unable to resolve expression: " + expression);
      }
      return value.toString();
    } catch (Exception e) {
      throw new FgaAuthorizationException("Error resolving expression: " + expression, e);
    }
  }
}
