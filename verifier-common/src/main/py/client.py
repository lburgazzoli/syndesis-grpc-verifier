import grpc

import health_pb2
import health_pb2_grpc

channel = grpc.insecure_channel('localhost:6565')
stub = health_pb2_grpc.HealthStub(channel)
repl = stub.health(health_pb2.HealthRequest())

print(repl.status)

