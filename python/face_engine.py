#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Python AI Engine for Face Recognition Comparison (Spring Boot MVC Integration)
Supports:
1. LBPH (OpenCV Classic - 4.1.2)
2. Dlib ResNet-34 (128-D CNN Embedding)
3. ArcFace / MobileFaceNet (InsightFace 512-D Embedding)
4. Evaluation Metrics (scikit-learn: Accuracy, Precision, Recall, F1, FAR, FRR - 5.4)
"""

import argparse
import json
import os
import sys
import time


def pad_image_for_detector(cv2, img):
    """Add border padding so RetinaFace / Dlib can detect tightly cropped faces reliably."""
    return cv2.copyMakeBorder(img, 100, 100, 100, 100, cv2.BORDER_REPLICATE)


def run_lbph(ref_path, target_path, threshold=65.0):
    t0 = time.perf_counter()
    try:
        import cv2
        import numpy as np

        recognizer = cv2.face.LBPHFaceRecognizer_create()
        img1 = cv2.imread(ref_path, cv2.IMREAD_GRAYSCALE)
        img2 = cv2.imread(target_path, cv2.IMREAD_GRAYSCALE)

        if img1 is None or img2 is None:
            return {
                "name": "1. LBPH (OpenCV Classic)",
                "status": "ERROR",
                "message": "ไม่สามารถอ่านไฟล์ภาพได้",
                "executionMs": round((time.perf_counter() - t0) * 1000, 2)
            }

        img1_resized = cv2.resize(img1, (200, 200))
        img2_resized = cv2.resize(img2, (200, 200))

        # สร้างภาพ Impostor จำลองเพื่อเป็นคลาสเปรียบเทียบในกรณีฝึกแบบ 1-to-1
        impostor = cv2.bitwise_not(img1_resized)
        faces = [img1_resized, impostor]
        labels = np.array([1, 2])

        recognizer.train(faces, labels)
        predicted_label, confidence = recognizer.predict(img2_resized)
        is_match = bool(predicted_label == 1 and confidence < threshold)

        return {
            "name": "1. LBPH (OpenCV Classic)",
            "dimension": "Histogram (Local Binary Patterns)",
            "metricName": "Chi-Square Distance (ยิ่งน้อยยิ่งเหมือน)",
            "score": round(float(confidence), 4),
            "threshold": f"< {threshold}",
            "match": is_match,
            "status": "OK",
            "message": f"ทำนายรหัส: {predicted_label} (Distance = {confidence:.2f})",
            "executionMs": round((time.perf_counter() - t0) * 1000, 2)
        }
    except Exception as e:
        return {
            "name": "1. LBPH (OpenCV Classic)",
            "dimension": "Histogram",
            "metricName": "Distance",
            "score": 0.0,
            "threshold": f"< {threshold}",
            "match": False,
            "status": "ERROR",
            "message": f"ต้องติดตั้ง opencv-contrib-python ({str(e)})",
            "executionMs": round((time.perf_counter() - t0) * 1000, 2)
        }


def run_dlib(ref_path, target_path, threshold=0.50):
    t0 = time.perf_counter()
    try:
        import cv2
        import face_recognition
        import numpy as np

        def get_encoding(path):
            img = face_recognition.load_image_file(path)
            encs = face_recognition.face_encodings(img)
            if len(encs) == 0:
                # ลองเติมขอบภาพ (Padding) หากภาพถูก Crop มาชิดเกินไป
                img_padded = pad_image_for_detector(cv2, img)
                encs = face_recognition.face_encodings(img_padded)
            return encs

        known_encs = get_encoding(ref_path)
        if len(known_encs) == 0:
            return {
                "name": "2. Dlib ResNet-34 (face_recognition)",
                "dimension": "128 มิติ (128-D Vector)",
                "metricName": "Euclidean Distance (< 0.50 = คนเดียวกัน)",
                "score": 0.0,
                "threshold": f"< {threshold}",
                "match": False,
                "status": "NO_FACE",
                "message": "ตรวจไม่พบใบหน้าในภาพต้นฉบับ (Reference)",
                "executionMs": round((time.perf_counter() - t0) * 1000, 2)
            }

        unknown_encs = get_encoding(target_path)
        if len(unknown_encs) == 0:
            return {
                "name": "2. Dlib ResNet-34 (face_recognition)",
                "dimension": "128 มิติ (128-D Vector)",
                "metricName": "Euclidean Distance (< 0.50 = คนเดียวกัน)",
                "score": 0.0,
                "threshold": f"< {threshold}",
                "match": False,
                "status": "NO_FACE",
                "message": "ตรวจไม่พบใบหน้าในภาพทดสอบ (Target)",
                "executionMs": round((time.perf_counter() - t0) * 1000, 2)
            }

        distance = float(np.linalg.norm(known_encs[0] - unknown_encs[0]))
        is_match = bool(distance < threshold)

        return {
            "name": "2. Dlib ResNet-34 (face_recognition)",
            "dimension": "128 มิติ (128-D Vector)",
            "metricName": "Euclidean Distance (ยิ่งน้อยยิ่งเหมือน)",
            "score": round(distance, 4),
            "threshold": f"< {threshold:.2f}",
            "match": is_match,
            "status": "OK",
            "message": f"ระยะห่าง Euclidean = {distance:.4f}",
            "executionMs": round((time.perf_counter() - t0) * 1000, 2)
        }
    except Exception as e:
        return {
            "name": "2. Dlib ResNet-34 (face_recognition)",
            "dimension": "128 มิติ (128-D Vector)",
            "metricName": "Euclidean Distance",
            "score": 0.0,
            "threshold": f"< {threshold:.2f}",
            "match": False,
            "status": "ERROR",
            "message": f"ต้องติดตั้ง face_recognition ({str(e)})",
            "executionMs": round((time.perf_counter() - t0) * 1000, 2)
        }


def run_insightface(ref_path, target_path, threshold=0.45):
    t0 = time.perf_counter()
    try:
        import cv2
        import numpy as np
        from insightface.app import FaceAnalysis

        # Redirect stdout temporarily so InsightFace model logs don't pollute JSON stdout
        old_stdout = sys.stdout
        sys.stdout = sys.stderr
        try:
            app = FaceAnalysis(name="buffalo_sc", providers=["CPUExecutionProvider"])
            app.prepare(ctx_id=0, det_size=(640, 640))

            def get_embedding(path):
                img = cv2.imread(path)
                if img is None:
                    return None
                faces = app.get(img)
                if len(faces) == 0:
                    img_padded = pad_image_for_detector(cv2, img)
                    faces = app.get(img_padded)
                if len(faces) == 0:
                    return None
                return faces[0].normed_embedding

            feat1 = get_embedding(ref_path)
            feat2 = get_embedding(target_path)
        finally:
            sys.stdout = old_stdout

        if feat1 is None or feat2 is None:
            missing_side = "ภาพต้นฉบับ (Reference)" if feat1 is None else "ภาพทดสอบ (Target)"
            return {
                "name": "3. ArcFace / MobileFaceNet (InsightFace)",
                "dimension": "512 มิติ (512-D Normed Vector)",
                "metricName": "Cosine Similarity (> 0.45 = คนเดียวกัน)",
                "score": 0.0,
                "threshold": f"> {threshold:.2f}",
                "match": False,
                "status": "NO_FACE",
                "message": f"RetinaFace ตรวจไม่พบใบหน้าใน{missing_side}",
                "executionMs": round((time.perf_counter() - t0) * 1000, 2)
            }

        cosine_sim = float(np.dot(feat1, feat2))
        is_match = bool(cosine_sim > threshold)

        return {
            "name": "3. ArcFace / MobileFaceNet (InsightFace)",
            "dimension": "512 มิติ (512-D Normed Vector)",
            "metricName": "Cosine Similarity (เข้าใกล้ 1.0 ยิ่งเหมือน)",
            "score": round(cosine_sim, 4),
            "threshold": f"> {threshold:.2f}",
            "match": is_match,
            "status": "OK",
            "message": f"ค่าความคล้าย Cosine Similarity = {cosine_sim:.4f}",
            "executionMs": round((time.perf_counter() - t0) * 1000, 2)
        }
    except Exception as e:
        return {
            "name": "3. ArcFace / MobileFaceNet (InsightFace)",
            "dimension": "512 มิติ (512-D Normed Vector)",
            "metricName": "Cosine Similarity",
            "score": 0.0,
            "threshold": f"> {threshold:.2f}",
            "match": False,
            "status": "ERROR",
            "message": f"ต้องติดตั้ง insightface และ onnxruntime ({str(e)})",
            "executionMs": round((time.perf_counter() - t0) * 1000, 2)
        }


def run_metrics(y_true_str, y_pred_str):
    try:
        from sklearn.metrics import (
            accuracy_score,
            confusion_matrix,
            f1_score,
            precision_score,
            recall_score,
        )

        y_true = [int(x.strip()) for x in y_true_str.split(",") if x.strip() != ""]
        y_pred = [int(x.strip()) for x in y_pred_str.split(",") if x.strip() != ""]

        if len(y_true) != len(y_pred) or len(y_true) == 0:
            raise ValueError("จำนวนข้อมูลใน y_true และ y_pred ต้องเท่ากันและไม่ว่างเปล่า")

        acc = float(accuracy_score(y_true, y_pred))
        prec = float(precision_score(y_true, y_pred, zero_division=0))
        rec = float(recall_score(y_true, y_pred, zero_division=0))
        f1 = float(f1_score(y_true, y_pred, zero_division=0))

        cm = confusion_matrix(y_true, y_pred, labels=[0, 1])
        tn, fp, fn, tp = [int(v) for v in cm.ravel()]

        far = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0
        frr = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0

        return {
            "status": "OK",
            "sampleCount": len(y_true),
            "accuracy": round(acc * 100, 2),
            "precision": round(prec * 100, 2),
            "recall": round(rec * 100, 2),
            "f1": round(f1 * 100, 2),
            "far": round(far * 100, 2),
            "frr": round(frr * 100, 2),
            "tp": tp,
            "tn": tn,
            "fp": fp,
            "fn": fn
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "message": str(e)
        }


def main():
    parser = argparse.ArgumentParser(description="Face Recognition Comparison Engine")
    subparsers = parser.add_subparsers(dest="mode", required=True)

    cmp_parser = subparsers.add_parser("compare")
    cmp_parser.add_argument("--ref", required=True, help="Path to reference face image")
    cmp_parser.add_argument("--target", required=True, help="Path to target/classroom face image")

    eval_parser = subparsers.add_parser("evaluate")
    eval_parser.add_argument("--y_true", required=True, help="Comma-separated ground truth labels (0,1)")
    eval_parser.add_argument("--y_pred", required=True, help="Comma-separated predicted labels (0,1)")

    args = parser.parse_args()

    if args.mode == "compare":
        results = [
            run_lbph(args.ref, args.target),
            run_dlib(args.ref, args.target),
            run_insightface(args.ref, args.target),
        ]
        print(json.dumps({"status": "OK", "algorithms": results}, ensure_ascii=False))
    elif args.mode == "evaluate":
        res = run_metrics(args.y_true, args.y_pred)
        print(json.dumps(res, ensure_ascii=False))


if __name__ == "__main__":
    main()
