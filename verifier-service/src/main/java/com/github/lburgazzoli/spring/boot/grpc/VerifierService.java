package com.github.lburgazzoli.spring.boot.grpc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.syndesis.verifier.api.Verifier;
import io.syndesis.verifier.api.VerifierResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
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
        context.setAutoStartup(false);
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

    @Bean
    public BindableService verifierGrpcService(ApplicationContext context, CamelContext camelContext) {
        return new VerifierGrpc.VerifierImplBase() {
            @Override
            public void verify(VerifierOuterClass.VerifyRequest request, StreamObserver<VerifierOuterClass.VerifyReply> responseObserver) {
                Verifier verifier;

                final String connector = request.getId();
                final Map<String, Object> options = new HashMap<>(request.getOptionsCount() == 0 ? new HashMap<>() : request.getOptionsMap());

                try {
                    verifier = context.getBean(connector, Verifier.class);
                } catch (NoSuchBeanException|NoSuchBeanDefinitionException ignored) {
                    LOGGER.warn("", ignored);

                    try {
                        // Then fallback to camel's factory finder
                        final FactoryFinder finder = camelContext.getFactoryFinder("META-INF/syndesis/connector/verifier/");
                        final Class<?> type = finder.findClass(connector);

                        verifier = (Verifier) camelContext.getInjector().newInstance(type);
                    } catch (Exception e) {
                        LOGGER.warn("", e);

                        verifier = null;
                    }
                }

                if (verifier != null) {
                    final List<VerifierResponse> responses = verifier.verify(camelContext, connector, options);

                    for (VerifierResponse response : responses) {
                        responseObserver.onNext(
                            VerifierOuterClass.VerifyReply.newBuilder()
                                .setMessage(response.getStatus().name())
                                .build()
                        );
                    }

                    responseObserver.onCompleted();
                } else {
                    responseObserver.onNext(
                        VerifierOuterClass.VerifyReply.newBuilder()
                            .setMessage("unknown-connector")
                            .build()
                    );
                    responseObserver.onCompleted();
                }

            }
        };
    }

    @Bean
    public BindableService healthGrpcService(HealthEndpoint endpoint) {
        return new HealthGrpc.HealthImplBase() {
            @Override
            public void health(HealthOuterClass.HealthRequest request, StreamObserver<HealthOuterClass.HealthReply> responseObserver) {
                final Health health = endpoint.invoke();

                responseObserver.onNext(
                    HealthOuterClass.HealthReply.newBuilder()
                        .setStatus(health.getStatus().getCode())
                        .setDescription(health.getStatus().getDescription())
                        .build()
                );

                responseObserver.onCompleted();
            }
        };
    }

    @Bean
    public HealthIndicator grpcHealthIndicator() {
	    final AtomicInteger counter = new AtomicInteger();

	    return new HealthIndicator() {
            @Override
            public Health health() {
                return new Health.Builder()
                    .status(counter.incrementAndGet() % 2 == 0 ? Status.UP : Status.DOWN)
                    .build();
            }
        };
    }
}
