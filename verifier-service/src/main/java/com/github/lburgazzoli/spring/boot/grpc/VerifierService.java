package com.github.lburgazzoli.spring.boot.grpc;

import java.util.List;
import java.util.Map;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.syndesis.verifier.api.Verifier;
import io.syndesis.verifier.api.VerifierResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    exclude = {
        ActiveMQAutoConfiguration.class,
        ArtemisAutoConfiguration.class,
        BatchAutoConfiguration.class,
        CacheAutoConfiguration.class,
        GsonAutoConfiguration.class
    }
)
public class VerifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerifierService.class);

    @Value("${grpc.port:6565}")
    private int port;

	public static void main(String[] args) throws Exception {
        ApplicationContext ctx = new SpringApplicationBuilder()
            .sources(VerifierService.class)
            .web(false)
            .build()
            .run(args);

        ctx.getBean(Server.class).awaitTermination();
    }

    @Bean(destroyMethod = "stop", initMethod = "start")
    public CamelContext camel() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.setName("grpc-verifier");
        context.disableJMX();

        return context;
    }

	@Bean(destroyMethod = "shutdown", initMethod = "start")
    public Server server(List<BindableService> services) {
        ServerBuilder builder = ServerBuilder.forPort(port);
        services.forEach(builder::addService);

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    @Bean
    public BindableService service(ApplicationContext context, CamelContext camelContext) {
        return new VerifierGrpc.VerifierImplBase() {
            @Override
            public void verify(VerifierOuterClass.VerifyRequest request, StreamObserver<VerifierOuterClass.VerifyReply> responseObserver) {

                try {
                    final String connector = request.getId();
                    final Map<String, Object> options = Map.class.cast(request.getOptionsMap());
                    final Verifier verifier = context.getBean(connector, Verifier.class);
                    final List<VerifierResponse> responses = verifier.verify(camelContext, connector, options);

                    for (VerifierResponse response : responses) {
                        responseObserver.onNext(
                            VerifierOuterClass.VerifyReply.newBuilder()
                                .setMessage(response.getStatus().name())
                                .build()
                        );
                    }

                    responseObserver.onCompleted();
                } catch (NoSuchBeanException e) {
                    LOGGER.warn("", e);
                }
            }
        };
    }
}
