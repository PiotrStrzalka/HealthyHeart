package com.automation.bear.healthyheartcamera;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.ArrayList;
import java.util.List;

import uk.me.berndporr.iirj.Butterworth;

/**
 * Created by Piotrek on 2018-04-17.
 */
class Measurement{
    double timeStampInc;
    double rawValue;
    double buttValue;

    public Measurement(double timeStampInc, double rawValue){
        this.rawValue = rawValue;
        this.timeStampInc = timeStampInc;
    }
}

public class HRAnalyzer {

    public class polyMeasurement{
        double timeStampInc;
        double value;
    }

    public List<Measurement> mMeasurementLst;
    int mListCounter;

    List<polyMeasurement> mPolyMeasurementLst;
    Butterworth mButterworth = new Butterworth();
    List<DataPoint> peakTimeLst = new ArrayList<>();
    public HRAnalyzer(){
       initData();
    }

    public void initData(){
        mListCounter = 0;
        mMeasurementLst = new ArrayList<>();
        mPolyMeasurementLst = new ArrayList<>();
        mButterworth = new Butterworth();
        initButterworth(mButterworth);
    }

    public void flushData(){
        mListCounter = 0;
        mMeasurementLst.clear();
        mPolyMeasurementLst.clear();

        mButterworth.reset();
        mButterworth = new Butterworth();
        initButterworth(mButterworth);
    }

    public void initButterworth(Butterworth mButterworth){
        mButterworth. bandPass(4,29, 1.83, 1.5);
        for(int i = 1; i<=200; i++){
            mButterworth.filter(240);
        }
    }

    public double addData(Measurement m){

            if(mMeasurementLst.size()==0){
            for(int i = 1; i<=200; i++){
                mButterworth.filter(m.rawValue);
            }
        }


        double afterButter =  mButterworth.filter(m.rawValue);
        m.buttValue = afterButter;
        mMeasurementLst.add(m);
        mListCounter++;

        return  afterButter;
    }

    public DataPoint[] getPolyPlot(int start, int stop){ //start and stop are numbers from List
        if(mMeasurementLst == null) return null;

        if(start == 0){
            peakTimeLst.clear();
        }
        if(peakTimeLst.size() == 0){
            peakTimeLst.add(new DataPoint(0.0,0.0));
        }

        int size = stop-start;

        double[] x = new double[size];
        double[] y = new double[size];

        for(int i = start; i < stop; i++){
            x[i-start] = mMeasurementLst.get(i).timeStampInc;
            y[i-start] = mMeasurementLst.get(i).buttValue;
        }

        SplineInterpolator mSplineInterpolator = new SplineInterpolator();
        PolynomialSplineFunction mPSF = mSplineInterpolator.interpolate(x,y);

        int g = (int)x[0]+1;
        int k = (int)x[x.length-1]-1;

        double dataPointSize = Math.ceil((k-g)/1.0);

        DataPoint[] mSeriesSpline = new DataPoint[(int)dataPointSize];
        int counter = 0;
        for(int i = g; i<k; i=i+1){
            double a = mPSF.value((double)i);
            mSeriesSpline[counter] = new DataPoint(i,a);

            //Searching for local max
            if((counter > 2) && (mSeriesSpline[counter-2].getY() < mSeriesSpline[counter-1].getY()) &&
                    (mSeriesSpline[counter-1].getY() > mSeriesSpline[counter].getY())){
                //if time is bigger than 500ms save it
                //if(mSeriesSpline[counter-1].getX()-peakTimeLst.get(peakTimeLst.size()-1).getX() > 650){
                    peakTimeLst.add(mSeriesSpline[counter-1]);
                //}

            }
            counter++;
            //mSeriesSpline.appendData(new DataPoint(i,a),true, 20000,true);
        }

        for(int i=0; i < peakTimeLst.size(); i++){

        }


        return mSeriesSpline;
    }

    public List<DataPoint> getPolyPlotLst(int start, int stop){ //start and stop are numbers from List
        if(mMeasurementLst == null) return null;

        int size = stop-start;

        double[] x = new double[size];
        double[] y = new double[size];

        for(int i = start; i < stop; i++){
            x[i-start] = mMeasurementLst.get(i).timeStampInc;
            y[i-start] = mMeasurementLst.get(i).buttValue;
        }

        SplineInterpolator mSplineInterpolator = new SplineInterpolator();
        PolynomialSplineFunction mPSF = mSplineInterpolator.interpolate(x,y);

        int g = (int)x[0]+1;
        int k = (int)x[x.length-1]-1;

        double dataPointSize = (k-g)/5.0;

        List<DataPoint> mSeriesSpline = new ArrayList<>();
        int counter = 0;
        for(int i = g; i<k; i=i+5){
            double a = mPSF.value((double)i);
            mSeriesSpline.add(new DataPoint(i,a));
            counter++;
            //mSeriesSpline.appendData(new DataPoint(i,a),true, 20000,true);
        }

        return mSeriesSpline;
    }

    public String getLastPeak(){
        if(peakTimeLst.size() < 2) return "0";

        Double lastTime = peakTimeLst.get(peakTimeLst.size()-1).getX() - peakTimeLst.get(peakTimeLst.size()-2).getX();
        return lastTime.toString();
    }

}
