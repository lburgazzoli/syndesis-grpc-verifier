package com.github.lburgazzoli.spring.boot.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifierClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerifierClient.class);

	public static void main(String[] args) throws Exception {
        final ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext(true).build();
        final VerifierGrpc.VerifierBlockingStub stub = VerifierGrpc.newBlockingStub(channel);


        for (int i = 0; i < 100; i++) {
            LOGGER.info("Request ...");
            VerifierOuterClass.VerifyReply reply;

            if (i % 2 == 0) {
                reply = stub.verify(
                    VerifierOuterClass.VerifyRequest.newBuilder()
                        .setId("twitter")
                        .build()
                );
            } else {
                reply = stub.verify(
                    VerifierOuterClass.VerifyRequest.newBuilder()
                        .setId("twitter")
                        .putOptions("consumerKey", "NMqaca1bzXsOcZhP2XlwA")
                        .putOptions("consumerSecret", "VxNQiRLwwKVD0K9mmfxlTTbVdgRpriORypnUbHhxeQw")
                        //.putOptions("accessToken", "26693234-W0YjxL9cMJrC0VZZ4xdgFMymxIQ10LeL1K8YlbBY")
                        //.putOptions("accessTokenSecret", "BZD51BgzbOdFstWZYsqB5p5dbuuDV12vrOdatzhY4E")
                        .build()
                );
            }

            LOGGER.info("Reply: {}", reply.getMessage());

            Thread.sleep(1000);
        }

        channel.shutdown();
    }
}
