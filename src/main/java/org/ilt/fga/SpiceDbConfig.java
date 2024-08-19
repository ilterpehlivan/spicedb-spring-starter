package org.ilt.fga;

import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpiceDbConfig {

  @Value("${spicedb.url:localhost}")
  private String spiceDbUrl;

  @Value("${spicedb.port:5001}")
  private int spiceDbPort;

  @Value("${spicedb.token:test}")
  private String spiceDbToken;

  @Value("${spicedb.is-secure:false}")
  private Boolean isTls;

  public SpiceDbConfig() {
  }

  public SpiceDbConfig(String spiceDbUrl, int spiceDbPort, String spiceDbToken, Boolean isTls) {
    this.spiceDbUrl = spiceDbUrl;
    this.spiceDbPort = spiceDbPort;
    this.spiceDbToken = spiceDbToken;
    this.isTls = isTls;
  }

  @Bean
  public PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionsService() {
    ManagedChannel channel = getChannel();

    return PermissionsServiceGrpc.newBlockingStub(channel)
        .withCallCredentials(new BearerToken(spiceDbToken));
  }

  private ManagedChannel getChannel() {
    ManagedChannelBuilder<?> managedChannelBuilder =
        ManagedChannelBuilder.forAddress(spiceDbUrl, Integer.valueOf(spiceDbPort));

    ManagedChannel channel;
    if (isTls) {
      channel = managedChannelBuilder.useTransportSecurity().build();
    } else {
      channel = managedChannelBuilder.usePlaintext().build();
    }
    return channel;
  }
}
