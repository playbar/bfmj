package com.baofeng.mj.vrplayer.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import static android.R.attr.x;
import static android.R.attr.y;

/**
 * Created by kekeyu on 2017/4/4.
 */

public class BitmapUtil {

    /**
     * 创建四个圆角背景
     * @param width
     * @param height
     * @return
     */
    public static Bitmap getBitmap(int width,int height, float rid,String color) {

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        paint.setARGB(152, 157, 168, 150);
        //"#BF7337"
        paint.setColor(Color.parseColor(color));
        RectF rectF = new RectF(0,0,bitmap.getWidth(),bitmap.getHeight());

        //canvas.drawRoundRect(rectF, 50f, 50f, paint);

        Path path = new Path();
        //向路径中添加圆角矩形。radii数组定义圆角矩形的四个圆角的x,y半径。radii长度必须为8
        //圆角的半径，依次为左上角xy半径，右上角，右下角，左下角
        float[] rids = {rid,rid,rid,rid,rid,rid,rid,rid};
        path.addRoundRect(rectF,rids,Path.Direction.CW);//Path.Direction.CW,顺时针绘制

        canvas.drawPath(path, paint);

        return bitmap;
    }

    public static Bitmap getBottomRoundBitmap(int width,int height, float rid,String color) {

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        paint.setARGB(152, 157, 168, 150);
        //"#BF7337"
        paint.setColor(Color.parseColor(color));
        RectF rectF = new RectF(0,0,bitmap.getWidth(),bitmap.getHeight());

        //canvas.drawRoundRect(rectF, 50f, 50f, paint);

        Path path = new Path();
        //向路径中添加圆角矩形。radii数组定义圆角矩形的四个圆角的x,y半径。radii长度必须为8
        //圆角的半径，依次为左上角xy半径，右上角，右下角，左下角
        float[] rids = {0f,0f,0f,0f,rid,rid,rid,rid};
        path.addRoundRect(rectF,rids,Path.Direction.CW);//Path.Direction.CW,顺时针绘制

        canvas.drawPath(path, paint);

        return bitmap;
    }

    /**
     * 将图片绘制成圆角
     * @param bitmap
     * @param width
     * @param height
     * @param rid
     * @return
     */
    public static Bitmap getRoundImage(Bitmap bitmap,int width,int height,float rid) {

        //根据源文件新建一个darwable对象
        Drawable imageDrawable = new BitmapDrawable(bitmap);

        // 新建一个新的输出图片
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // 新建一个矩形
        RectF outerRect = new RectF(0, 0, width, height);

        // 产生一个红色的圆角矩形
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        //paint.setColor(Color.RED);
        canvas.drawRoundRect(outerRect, rid, rid, paint);

        // 将源图片绘制到这个圆角矩形上
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        imageDrawable.setBounds(0, 0, width, height);
        canvas.saveLayer(outerRect, paint, Canvas.ALL_SAVE_FLAG);
        imageDrawable.draw(canvas);
        canvas.restore();

        return output;
    }
}
