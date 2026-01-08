package com.example.imagepicker;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class MyBBoxView extends View {
    Paint boxPaint;
    Paint textPaint;

    List detections_list;

    // create context
    public MyBBoxView(Context context, List detections)
    {
        super(context);
        // create shape objects
        boxPaint = new Paint();
        textPaint = new Paint();

        // give detections
        detections_list = detections;
    }

    // drawing class
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        List<List<Double>> boxes_reduced = (List<List<Double>>) detections_list.get(0);
        List<Float> scores_reduced = (List<Float>) detections_list.get(1);
        List<String> labels_reduced = (List<String>) detections_list.get(2);

        // draw a red rectangle
        boxPaint.setColor(Color.RED);
        // make frame type rectangle (transparent)
        boxPaint.setStyle(Paint.Style.STROKE);
        // width of frame
        boxPaint.setStrokeWidth(6);

        // draw text
        textPaint.setColor(Color.WHITE);
        // adjust text size
        textPaint.setTextSize(10);

        // get image size
        int x_width = getWidth();
        int y_height = getHeight();

        // init temp variables
        List<Double> current_bbox;
        float current_score;
        String current_label;

        // init bbox coord variables
        float x0_coord_left;
        float y0_coord_top;
        float x1_coord_right;
        float y1_coord_bottom;

        int offset_val = 10;

        // make bbox algorithm
        for (int i = 0; i < scores_reduced.size(); i++) {

            // get current detections (bboxes, score, label)
            current_bbox = boxes_reduced.get(i);
            current_score = scores_reduced.get(i);
            current_label = labels_reduced.get(i);

            // bboxes coordinates
            x0_coord_left = current_bbox.get(0).floatValue();
            y0_coord_top = current_bbox.get(1).floatValue();
            x1_coord_right = current_bbox.get(2).floatValue();
            y1_coord_bottom = current_bbox.get(3).floatValue();

            // insert data
            canvas.drawRect(x_width*x0_coord_left, y_height*y0_coord_top,
                           x_width*x1_coord_right, y_height*y1_coord_bottom, boxPaint);

            // score text
            canvas.drawText(String.valueOf(current_score), x_width*x0_coord_left - offset_val, (y_height*y0_coord_top) - offset_val, textPaint);
            // label text
            canvas.drawText(String.valueOf(current_label), x_width*x1_coord_right - offset_val, y_height*y0_coord_top - offset_val, textPaint);
        }


        Log.d("BackResponseDetections", detections_list.toString());

        Log.d("BackResponseDetections", boxes_reduced.toString());
        Log.d("BackResponseDetections", scores_reduced.toString());
        Log.d("BackResponseDetections", labels_reduced.toString());

    }
}
