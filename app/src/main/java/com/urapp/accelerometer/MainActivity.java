package com.urapp.accelerometer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.util.Locale;
import android.widget.Toast;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private int sampleCount = 0;
    private boolean isRecording = false;
    private boolean isFirst = true;
    private FileWriter writer;
    private File file;
    private ParcelFileDescriptor pfd;
    private Uri uri;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView textView, textInfo;
    private EditText sampleInput, waitInput;
    private Button button;
    private LineChart linechart;
    private LineData lineData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        textInfo = findViewById(R.id.text_info);
        textView = findViewById(R.id.text_view);
        button = findViewById(R.id.button);
        sampleInput = findViewById(R.id.sample_input);
        waitInput = findViewById(R.id.wait_input);
        linechart = findViewById(R.id.chart);
        button.setOnClickListener(v -> onClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeFile();
    }

    public void onClick(){
        if(isFirst){
            setFileLocation();
            isFirst = false;
            button.setText("測定開始");
        }else{
            lineData = new LineData();
            LineDataSet dataSet = new LineDataSet(null, "Acceleration");
            dataSet.setDrawValues(false);
            lineData.addDataSet(dataSet);
            linechart.setData(lineData);
            linechart.getDescription().setEnabled(false);
            new Handler().postDelayed(() -> {
                isRecording = true;
            }, Integer.parseInt(waitInput.getText().toString()) * 1000L);
        }
    }

    public void setFileLocation() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "accelerometer_" + timeStamp + ".csv");
        resultLauncher.launch(intent);
    }

    public void openFile(){
        try {
            pfd = getContentResolver().openFileDescriptor(uri, "w");
            file = new File(uri.getPath());
            writer = new FileWriter(pfd.getFileDescriptor());
            writer.append("X,Y,Z\n");
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "File not found: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void closeFile() {
        try {
            writer.flush();
            writer.close();
            pfd.close();
            Toast.makeText(this, "Recording saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent resultData = result.getData();
                    if (resultData != null) {
                        uri = resultData.getData();
                        openFile();
                    }
                }
            });

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float ax = event.values[0], ay = event.values[1], az = event.values[2];
            float a = (float)Math.sqrt(ax * ax + ay * ay + az * az);
            String strTmp = String.format(Locale.US,"加速度\n X: %.10f\n Y: %.10f\n Z: %.10f\n a = %.10f m/s^2", ax, ay, az, a);
            textView.setText(strTmp);
            showInfo(event);
            int sample = Integer.parseInt(sampleInput.getText().toString());
            if (isRecording & sampleCount < sample) {
                try {
                    writer.append(String.format(Locale.US,"%.10f,%.10f,%.10f\n", ax, ay, az));
                    button.setText(String.format(Locale.US,"測定中 %d/%d", sampleCount, sample));
                    lineData.addEntry(new Entry(sampleCount, a),0);
                    lineData.notifyDataChanged();
                    linechart.notifyDataSetChanged();
                    linechart.invalidate();
                    sampleCount++;
                } catch (IOException e) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }else if(sampleCount == sample){
                isRecording = false;
                sampleCount = 0;
                closeFile();
                openFile();
                button.setText("再測定");
            }
        }
    }

    private void showInfo(SensorEvent event) {
        StringBuffer info = new StringBuffer("Name: ");
        info.append(event.sensor.getName());
        info.append("\n");

        info.append("Vendor: ");
        info.append(event.sensor.getVendor());
        info.append("\n");

        info.append("Type: ");
        info.append(event.sensor.getType());
        info.append("\n");

        int data = event.sensor.getMinDelay();
        info.append("MinDelay: ");
        info.append(data);
        info.append(" usec\n");

        data = event.sensor.getMaxDelay();
        info.append("MaxDelay: ");
        info.append(data);
        info.append(" usec\n");

        data = event.sensor.getReportingMode();
        String stinfo = "unknown";
        if (data == 0) {
            stinfo = "REPORTING_MODE_CONTINUOUS";
        } else if (data == 1) {
            stinfo = "REPORTING_MODE_ON_CHANGE";
        } else if (data == 2) {
            stinfo = "REPORTING_MODE_ONE_SHOT";
        }
        info.append("ReportingMode: ");
        info.append(stinfo);
        info.append("\n");

        info.append("MaxRange: ");
        float fData = event.sensor.getMaximumRange();
        info.append(fData);
        info.append("\n");

        info.append("Resolution: ");
        fData = event.sensor.getResolution();
        info.append(fData);
        info.append(" m/s^2\n");

        info.append("Power: ");
        fData = event.sensor.getPower();
        info.append(fData);
        info.append(" mA");

        textInfo.setText(info);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
}
