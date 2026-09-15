import onnx
from onnx import helper
from onnx import TensorProto
import numpy as np

images = helper.make_tensor_value_info('images', TensorProto.FLOAT, [1, 3, 640, 640])
output = helper.make_tensor_value_info('output', TensorProto.FLOAT, [1, 84, 8400])

dummy_output = np.zeros((1, 84, 8400), dtype=np.float32)
dummy_output[0, 0, 0] = 320 
dummy_output[0, 1, 0] = 320 
dummy_output[0, 2, 0] = 200 
dummy_output[0, 3, 0] = 100 
dummy_output[0, 4, 0] = 0.95 

const_tensor = helper.make_tensor(
    name='const_out',
    data_type=TensorProto.FLOAT,
    dims=(1, 84, 8400),
    vals=dummy_output.flatten().tolist()
)

const_node = helper.make_node(
    'Constant',
    inputs=[],
    outputs=['const_out'],
    value=const_tensor,
    name='const_node'
)

node_identity = helper.make_node(
    'Identity',
    inputs=['const_out'],
    outputs=['output'],
)

graph_def = helper.make_graph(
    [const_node, node_identity],
    'dummy_yolov8',
    [images],
    [output],
)

# Use an older opset (e.g., opset 17) supported by onnxruntime!
op = onnx.OperatorSetIdProto()
op.version = 17

model_def = helper.make_model(graph_def, producer_name='dummy_maker', opset_imports=[op])
onnx.save(model_def, '/home/damon/AndroidStudioProjects/ShipTracker/app/src/main/assets/ship_detector.onnx')
print("Replaced with older opset!")
