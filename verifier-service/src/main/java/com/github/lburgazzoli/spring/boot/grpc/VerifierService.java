package com.github.lburgazzoli.spring.boot.grpc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.cloud.CloudAutoConfiguration;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
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
        RedisAutoConfiguration.class,
        GsonAutoConfiguration.class,
        JmsAutoConfiguration.class,
        CassandraAutoConfiguration.class,
        CassandraDataAutoConfiguration.class,
        CassandraRepositoriesAutoConfiguration.class,
        CloudAutoConfiguration.class,
        CouchbaseAutoConfiguration.class,
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
    public Server server(BindableService service) {
        return ServerBuilder.forPort(port)
            .addService(service)
            .build();
    }

    @Bean
    public BindableService service(CamelContext camelContext) {
        return new VerifierGrpc.VerifierImplBase() {
            @Override
            public void verify(VerifierOuterClass.VerifyRequest request, StreamObserver<VerifierOuterClass.VerifyReply> responseObserver) {
                Component component = camelContext.getComponent(request.getId());
                if (component != null) {
                    Optional<ComponentVerifierExtension> extension = component.getExtension(ComponentVerifierExtension.class);
                    if (extension.isPresent()) {

                        LOGGER.info("Id: {}, options: {}", request.getId(), request.getOptionsMap());

                        ComponentVerifierExtension.Result result = extension.get().verify(
                            ComponentVerifierExtension.Scope.CONNECTIVITY,
                            Map.class.cast(new HashMap<>(request.getOptionsMap()))
                        );

                        LOGGER.info("Result: {}", result);
                        responseObserver.onNext(
                            VerifierOuterClass.VerifyReply.newBuilder()
                                .setMessage(result.getStatus().name())
                                .build()
                        );
                        responseObserver.onCompleted();
                    } else {
                        LOGGER.info("Component {} does not support validation", request.getId());
                    }
                } else {
                    LOGGER.info("Unknown component {}", request.getId());
                }
            }
        };
    }
}
