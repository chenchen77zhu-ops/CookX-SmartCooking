from ultralytics import YOLO

model = YOLO('app/models/best.pt')

results = model.predict('test_food.png')

print("所有食材类别：", results[0].names)

print("识别到的类别索引：", results[0].boxes.cls)

detected_classes = [results[0].names[int(cls)] for cls in results[0].boxes.cls]
print("识别到的食材：", detected_classes)

results[0].show()