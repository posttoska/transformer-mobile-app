# uvicorn main:app
# uvicorn main:app --reload

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
    "http://127.0.0.1:8000"
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
async def post_image(file: UploadFile = File(...)):

    # save file from frontend
    with open(file.filename, "wb") as buffer:
        buffer.write(file.file.read())

    # read saved file as image
    image = cv2.imread(file.filename)

    # change cv2's BGR image to RGB image
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

    # define a transform
    transform = transforms.Compose([transforms.ToTensor()])

    # convert the image to torch tensor
    input_tensor = transform(image)

    # --- LOGGING ---
    # print(input_tensor)

    # call model
    output = await model_call(input_tensor)

    # proccess output (get detections: 0:boxes, 1:scores, 2:labels)
    output = output['detections'][0]['boxes']

    # convert torch tensor to simple list
    output = [ [num.item() for num in row] for row in output]

    # --- LOGGING ---
    print(output)

    # get prediction back (DEMO)
    return output

@app.post('/model-call')
async def model_call(input_tensor):
    
    # init model bellow 

    # select device
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

    # set model configuration
    config = CONFIG

    # additional setup
    num_classes = 21
    bg_class_idx = num_classes - 1

    # init model
    model = DETR(config, num_classes=num_classes, bg_class_idx=bg_class_idx).to(device)
    model.training = False

    # call model
    with torch.no_grad():
        output = model(input_tensor)

    return output