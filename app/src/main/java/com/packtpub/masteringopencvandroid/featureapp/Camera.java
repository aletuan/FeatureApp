package com.packtpub.masteringopencvandroid.featureapp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.Random;

public class Camera extends Activity {

    public final static String TAG = "FeatureApp::Camera";

    private final int SELECT_PHOTO = 0;
    private final int READ_PERMISSION = 1;

    private Mat     mOriginalMat;
    private Bitmap  mCurrentBitmap;     // bitmap to be applied with different algorithm
    private Bitmap  mOriginalBitmap;    // store original bitmap for compare
    private Uri     mSelectedImage;     // uri of selected image

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            Log.d(TAG, "onManagerConnected is called");
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("OpenCV Status", "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate is called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Initializing OpenCV Package Manager
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, mLoaderCallback);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu
        getMenuInflater().inflate(R.menu.filename, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.open_gallery) {
            Intent intent = new Intent(Intent.ACTION_PICK, Uri.parse("content://media/internal/images/media"));
            startActivityForResult(intent, SELECT_PHOTO);
        } else if (id == R.id.DoG) {
            DifferenceOfGaussian();

        } else if (id == R.id.CannyEdges) {
            Canny();
        } else if (id == R.id.SobelFilter) {
            Sobel();
        } else if (id == R.id.HarrisCorners) {
            HarrisCorner();
        } else if (id == R.id.HoughLines) {
            HoughLines();
        }

        return super.onOptionsItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == SELECT_PHOTO && resultCode == RESULT_OK && intent != null) {

            // Get URI selected image
            mSelectedImage = intent.getData();

            // Adding checking for permission
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE);

            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "Permission is not allowed. Request permission");

                ActivityCompat.requestPermissions(this, new String[]
                        {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION);

            } else {

                // TODO: Handle for the case permission is granted
                // This is for the case, user select another image

                //mCurrentBitmap = mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, false);
                mCurrentBitmap = rotateImage(mSelectedImage);

                // Update view
                loadImageToImageView();

            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case READ_PERMISSION: {
                // If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission is granted");

                    //mCurrentBitmap = mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    mCurrentBitmap = rotateImage(mSelectedImage);

                    // Update view
                    loadImageToImageView();

                } else {
                    Log.d(TAG, "Permission is Not granted");
                }
            }
        }
    }

    private Bitmap rotateImage(Uri selectedImage) {
        String filePath = getFilePath(selectedImage);

        // To speed up loading of image
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;

        Bitmap temp = BitmapFactory.decodeFile(filePath, options);


        // Get orientation information
        int orientation = 0;

        try {
            ExifInterface imgParams = new ExifInterface(filePath);
            orientation = imgParams.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);

        } catch (IOException e) {
            e.printStackTrace();
        }

        //Rotating the image to get the correct orientation
        Matrix rotate90 = new Matrix();
        rotate90.postRotate(orientation);

        // TODO: This function has side effect. Please fix it.
        mOriginalBitmap = rotateBitmap(temp, orientation);

        //Convert Bitmap to Mat
        Bitmap tempBitmap = mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        mOriginalMat = new Mat(tempBitmap.getHeight(), tempBitmap.getWidth(), CvType.CV_8U);
        Utils.bitmapToMat(tempBitmap, mOriginalMat);

        return mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    public String getFilePath(Uri img) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(img, filePathColumn,
                null, null, null);
        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String filePath = cursor.getString(columnIndex);
        cursor.close();

        return filePath;
    }

    private void loadImageToImageView() {
        ImageView imgView = (ImageView) findViewById(R.id.image_view);
        imgView.setImageBitmap(mCurrentBitmap);
    }

    //Function to rotate bitmap according to image parameters
    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * DoG Algorithm
     */
    /*
    public void DifferenceOfGaussian() {
        Mat DoG = new Mat();
        Mat blur1 = new Mat();
        Mat blur2 = new Mat();
        Mat grayMat = new Mat();

        // Converting the image to grayscale
        Imgproc.cvtColor(mOriginalMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        // Blur the image using two different blurring radius
        Imgproc.GaussianBlur(grayMat, blur1, new Size(15, 15), 5);
        Imgproc.GaussianBlur(grayMat, blur1, new Size(21, 21), 5);

        // Subtract the two blurred images
        Core.absdiff(blur1, blur2, DoG);

        // Inverse Binary Thresholding
        Core.multiply(DoG, new Scalar(100), DoG);
        Imgproc.threshold(DoG, DoG, 50, 255, Imgproc.THRESH_BINARY_INV);

        // Converting Mat back to Bitmap
        Utils.matToBitmap(DoG, mCurrentBitmap);
        loadImageToImageView();
    }
    */

    //Difference of Gaussian
    public void DifferenceOfGaussian() {
        Mat grayMat = new Mat();
        Mat blur1 = new Mat();
        Mat blur2 = new Mat();

        //Converting the image to grayscale
        Imgproc.cvtColor(mOriginalMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Imgproc.GaussianBlur(grayMat, blur1, new Size(15, 15), 5);
        Imgproc.GaussianBlur(grayMat, blur2, new Size(21, 21), 5);

        //Subtracting the two blurred images
        Mat DoG = new Mat();
        Core.absdiff(blur1, blur2, DoG);

        //Inverse Binary Thresholding
        Core.multiply(DoG, new Scalar(100), DoG);
        Imgproc.threshold(DoG, DoG, 50, 255, Imgproc.THRESH_BINARY_INV);

        //Converting Mat back to Bitmap
        //Utils.matToBitmap(DoG, mCurrentBitmap);
        Utils.matToBitmap(DoG, mCurrentBitmap);
        loadImageToImageView();
    }

    //Canny Edge Detection
    public void Canny() {

        Mat grayMat = new Mat();
        Mat cannyEdges = new Mat();

        //Converting the image to grayscale
        Imgproc.cvtColor(mOriginalMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Imgproc.Canny(grayMat, cannyEdges, 50, 100);

        //Converting Mat back to Bitmap
        Utils.matToBitmap(cannyEdges, mCurrentBitmap);
        loadImageToImageView();
    }

    //Sobel operator
    public void Sobel() {
        Mat grayMat = new Mat();
        Mat sobel = new Mat();      // Mat to store the result

        // Mat to store gradient and absolute gradient respectively
        Mat grad_x = new Mat();
        Mat abs_grad_x = new Mat();

        Mat grad_y = new Mat();
        Mat abs_grad_y = new Mat();

        //Converting the image to grayscale
        Imgproc.cvtColor(mOriginalMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        //Calculating gradient in horizontal direction
        Imgproc.Sobel(grayMat, grad_x, CvType.CV_16S, 1, 0, 3, 1, 0);

        //Calculating gradient in vertical direction
        Imgproc.Sobel(grayMat, grad_y, CvType.CV_16S, 0, 1, 3, 1, 0);

        //Calculating absolute value of gradients in both the direction
        Core.convertScaleAbs(grad_x, abs_grad_x);
        Core.convertScaleAbs(grad_y, abs_grad_y);

        //Calculating the resultant gradient
        Core.addWeighted(abs_grad_x, 0.5, abs_grad_y, 0.5, 1, sobel);

        //Converting Mat back to Bitmap
        Utils.matToBitmap(sobel, mCurrentBitmap);
        loadImageToImageView();

    }

    public void HarrisCorner() {
        Mat grayMat = new Mat();
        Mat corners = new Mat();

        //Converting the image to grayscale
        Imgproc.cvtColor(mOriginalMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Mat tempDst = new Mat();
        //finding contours
        Imgproc.cornerHarris(grayMat, tempDst, 2, 3, 0.04);

        //Normalizing harris corner's output
        Mat tempDstNorm = new Mat();
        Core.normalize(tempDst, tempDstNorm, 0, 255, Core.NORM_MINMAX);
        Core.convertScaleAbs(tempDstNorm, corners);

        //Drawing corners on a new image
        Random r = new Random();
        for (int i = 0; i < tempDstNorm.cols(); i++) {
            for (int j = 0; j < tempDstNorm.rows(); j++) {
                double[] value = tempDstNorm.get(j, i);
                if (value[0] > 150)
                    Core.circle(corners, new Point(i, j), 5, new Scalar(r.nextInt(255)), 2);
            }
        }

        //Converting Mat back to Bitmap
        Utils.matToBitmap(corners, mCurrentBitmap);
        loadImageToImageView();
    }

    public void HoughLines() {

        Mat grayMat = new Mat();
        Mat cannyEdges = new Mat();
        Mat lines = new Mat();

        //Converting the image to grayscale
        Imgproc.cvtColor(mOriginalMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Imgproc.Canny(grayMat, cannyEdges, 10, 100);

        Imgproc.HoughLinesP(cannyEdges, lines, 1, Math.PI / 180, 50, 20, 20);

        Mat houghLines = new Mat();
        houghLines.create(cannyEdges.rows(), cannyEdges.cols(), CvType.CV_8UC1);

        //Drawing lines on the image
        for (int i = 0; i < lines.cols(); i++) {
            double[] points = lines.get(0, i);
            double x1, y1, x2, y2;

            x1 = points[0];
            y1 = points[1];
            x2 = points[2];
            y2 = points[3];

            Point pt1 = new Point(x1, y1);
            Point pt2 = new Point(x2, y2);

            //Drawing lines on an image
            Core.line(houghLines, pt1, pt2, new Scalar(255, 0, 0), 1);
        }

        //Converting Mat back to Bitmap
        Utils.matToBitmap(houghLines, mCurrentBitmap);
        loadImageToImageView();
    }


}
