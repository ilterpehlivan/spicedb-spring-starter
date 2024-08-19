package org.ilt.fga;

import com.authzed.api.v1.PermissionsServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "spicedb.authorization.enabled", havingValue = "true")
@Import({SpiceDbConfig.class})
public class SpiceDbAutoConfiguration {

  @Bean
  public SpiceDbAuthorizeAspect spiceDbAuthorizationAspect(
      PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService) {
    return new SpiceDbAuthorizeAspect(permissionsService);
  }
}
