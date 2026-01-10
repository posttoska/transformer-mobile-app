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
from typing import List, Union
from fastapi import Depends
from datetime import datetime

from pydantic import BaseModel
from sqlalchemy import create_engine, Column, Integer, String, Float, Boolean, Text, LargeBinary
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

# init app
app = FastAPI()

# database setup
DATABASE_URL = "mssql+pyodbc://@DESKTOP-J0N5V67\\SQLEXPRESS/transformerDB?driver=ODBC+Driver+17+for+SQL+Server&Trusted_Connection=yes"

# create database engine
engine = create_engine(DATABASE_URL)

# create local session
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# sql alchemy database
Base = declarative_base()

# image + detections class model
class DetectionDB(Base):
    __tablename__ = 'detections'
    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    filename = Column(String(100), nullable=False)
    image_bytes = Column(LargeBinary, nullable=False)
    detections_json = Column(Text, nullable=False)

# create tables
Base.metadata.create_all(bind=engine)

# specify data types
class DetectionCreate(BaseModel):
    filename: str
    image_bytes: bytes
    detections_json: str

class Detection(DetectionCreate):
    id: int

    class Config:
        orm_mode = True

# dependency to get DB session
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

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
async def post_image(file: UploadFile = File(...), db: Session = Depends(get_db)) -> list:

    # read bytes for db
    file_bytes = file.file.read()

    # get file extention, or just put .png 
    # [0] - photo; [1] - extention
    ext = os.path.splitext(file.filename)[1] or ".png"
    # get datetime
    dt = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    # form file name
    saved_filename = f"{dt}{ext}"

    # save file from frontend
    with open(saved_filename, "wb") as buffer:
        buffer.write(file_bytes)

    # read saved file as image
    image = cv2.imread(saved_filename)

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

    # save prediction to database
    
    db_det = DetectionDB(
        filename=saved_filename,
        image_bytes=file_bytes,
        detections_json=json.dumps(output_list),
    )
    # commit and refresh
    db.add(db_det)
    db.commit()
    db.refresh(db_det)

    # get prediction back to frontend
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


# get last 30 database detection
@app.get("/detections", response_model=List[Detection])
async def get_detections(db: Session = Depends(get_db)):
    # order query response by id
    return (db.query(DetectionDB).order_by(DetectionDB.id.desc()).limit(30).all())



# get detection
@app.get("/detections/search/", response_model=List[Detection])
async def get_detection(filename: Union[str, None] = None, db: Session = Depends(get_db)):
    
    # create query object
    q = db.query(DetectionDB)

    # if query exists
    if filename:
        # get this query
        q = q.filter(DetectionDB.filename.contains(filename))

    return q.all()

# post detection
@app.post("/detection", response_model=Detection)
async def create_detection(detection: DetectionCreate, db: Session = Depends(get_db)):
    # create new detection and add to db
    db_detection = DetectionDB(**detection.dict())
    db.add(db_detection)
    db.commit()
    db.refresh(db_detection)
    return db_detection


# delete detection
@app.delete("/detections/{detection_id}", response_model=dict)
def delete_detection(detection_id: int, db: Session = Depends(get_db)):

    # find detection
    db_det = db.query(DetectionDB).filter(DetectionDB.id == detection_id).first()

    # not found error if there is no such detection
    if not db_det:
        raise HTTPException(status_code=404, detail="Detection not found")
    
    # deleted object
    deleted = {
        "id": db_det.id,
        "filename": db_det.filename,
        "detections_json": db_det.detections_json,
    }

    # delete, commit and 
    db.delete(db_det)
    db.commit()

    return deleted

# get detection by id
@app.get("/detections/{detection_id}", response_model=dict)
def get_detection(detection_id: int, db: Session = Depends(get_db)):

    # find detection
    db_det = db.query(DetectionDB).filter(DetectionDB.id == detection_id).first()

    # not found error if there is no such detection
    if not db_det:
        raise HTTPException(status_code=404, detail="Detection not found")

    return {
        "id": db_det.id,
        "filename": db_det.filename,
        "detections_json": db_det.detections_json,
    }