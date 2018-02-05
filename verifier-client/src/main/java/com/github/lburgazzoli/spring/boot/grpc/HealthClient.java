package com.github.lburgazzoli.spring.boot.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthClient.class);

	public static void main(String[] args) throws Exception {
        final ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext(true).build();
        final HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel);
        final HealthOuterClass.HealthReply reply = stub.health(HealthOuterClass.HealthRequest.newBuilder().build());

        LOGGER.info("status={}, description={}", reply.getStatus(), reply.getDescription());

        channel.shutdown();
    }
}
