from ultralytics import YOLO

class FoodDetector:
    def __init__(self, model_path="app/models/best.pt"):
        self.model = YOLO(model_path)

    def detect(self, image_path):
        results = self.model(image_path)
        # 提取识别到的类别名称
        names = results[0].names
        detected_items = [names[int(c)] for c in results[0].boxes.cls]
        return list(set(detected_items)) # 去重返回