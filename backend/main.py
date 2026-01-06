# uvicorn main:app
# uvicorn main:app --reload
# uvicorn main:app --reload --host 0.0.0.0 --port 8000
# uvicorn main:app --reload --host 192.168.1.177 --port 8000

# main imports

import json
import os
import cv2
import torch
import torchvision.transforms as transforms
from fastapi.middleware.cors import CORSMiddleware
from fastapi import FastAPI, File, UploadFile, HTTPException, Request
from model.detr import DETR
from model.config import CONFIG


# init app
app = FastAPI()

# CORS (Cross-Origin Resource Sharing) - resourses we're accepting
origins = [
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:4173",
    "http://localhost:4174",
    "http://localhost:3000",
    "http://127.0.0.1:8000",
    "http://192.168.1.245:8000"
]

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# main our-API page
@app.get("/")
async def root():
    return {"message": "welcome to detr detection app api!"}


# our-API check health page
@app.get("/health")
async def check_health():
    return {"message": "healthy"}


# post image
@app.post("/post-image")
async def post_image(file: UploadFile = File(...)) -> list:

    # save file from frontend
    with open(file.filename, "wb") as buffer:
        buffer.write(file.file.read())

    # read saved file as image
    image = cv2.imread(file.filename)

    # change cv2's BGR image to RGB image
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

    # transform to 640x640
    image = cv2.resize(image, (640, 640))

    # define a transform
    transform = transforms.Compose([transforms.ToTensor()])

    # convert the image to torch tensor
    input_tensor = transform(image)

    # call model
    output_dict = model_call(input_tensor)

    # init output list [detections, scores, labels]
    output_list = []

    # proccess output (get detections (boxes): 0:boxes, 1:scores, 2:labels)
    boxes_tensor = output_dict['detections'][0]['boxes']
    # convert torch tensor to simple list
    boxes = [ [num.item() for num in row] for row in boxes_tensor]

    # proccess output (get detections (scores): 0:boxes, 1:scores, 2:labels)
    scores_tensor = output_dict['detections'][0]['scores']
    # convert torch tensor to simple list
    scores = [num.item() for num in scores_tensor]

    # proccess output (get detections (labels): 0:boxes, 1:scores, 2:labels)
    labels_tensor = output_dict['detections'][0]['labels']
    # convert torch tensor to simple list
    labels = [num.item() for num in labels_tensor]

    # append stuff
    output_list.append(boxes)
    output_list.append(scores)
    output_list.append(labels)

    # LOGGING
    print(output_list)

    # get prediction back
    return output_list

def model_call(input_tensor):
    
    # init model bellow

    # select device
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

    # set model configuration
    config = CONFIG

    # additional setup
    num_classes = 23
    bg_class_idx = num_classes - 1

    # init model
    model = DETR(config, num_classes=num_classes, bg_class_idx=bg_class_idx).to(device)
    
    # specify weights path
    pth_path = r"C:\Users\posttoska\Documents\transformer-mobile-app\backend\model\weights\detr_voc23cls_plus_mydata_ep800.pth"

    # define state
    state = torch.load(pth_path, map_location=device)

    # load state
    model.load_state_dict(state, strict=True)

    # select inference mode
    model.eval()

    # call model
    with torch.inference_mode():
        output_dict = model(input_tensor)

    return output_dict