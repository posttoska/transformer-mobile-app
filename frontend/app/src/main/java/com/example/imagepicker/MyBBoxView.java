package com.example.imagepicker;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

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

        // draw a red rectangle
        boxPaint.setColor(Color.RED);
        // make frame type rectangle (transparent)
        boxPaint.setStyle(Paint.Style.STROKE);
        // width of frame
        boxPaint.setStrokeWidth(6);

        // draw text
        textPaint.setColor(Color.WHITE);
        // adjust text size
        textPaint.setTextSize(50);


        // insert data
        canvas.drawRect(100, 150, 500, 300, boxPaint);
        // percent text
        canvas.drawText("Percents1", 0, 340, textPaint);
        // class text
        canvas.drawText("Class1", 120, 180, textPaint);

        // insert data
        canvas.drawRect(500, 500, 300, 100, boxPaint);
        // percent text
        canvas.drawText("Percents2", 500, 350, textPaint);
        // class text
        canvas.drawText("Class2", 300, 500, textPaint);

    }

}
