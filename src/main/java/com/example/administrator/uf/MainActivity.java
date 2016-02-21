package com.example.administrator.uf;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.view.annotation.ViewInject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 多线程断点续传DEMO
 */
public class MainActivity extends Activity implements View.OnClickListener{
    @ViewInject(R.id.totalsize)private TextView totalSize;//总大小
    @ViewInject(R.id.havedown)private TextView havedown;//已下载
    @ViewInject(R.id.result)private TextView tv_result;//下载结果
    @ViewInject(R.id.begin)private Button begin;//开始下载
    @ViewInject(R.id.pause)private Button pause;//暂停
    @ViewInject(R.id.delete)private Button delete;//删除
    @ViewInject(R.id.ifexist)private Button ifexist;//是否有这文件
    File file = new File(Environment.getExternalStorageDirectory(),"测试下载.apk");
    URL url;
    StringBuilder builder = null;
    private int down = 0;//标记已下载大小
    private int allsize = 0;//标记文件大小
    private boolean isDown = false;//判断是否正在下载
    List<SingleDownloadThread> lists = new ArrayList<>();//下载的线程
    Map<Integer,Entity> map = new HashMap();//标记下载的进度
    private int N = 3;//分成几部分下载
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        ViewUtils.inject(this);
        init();
    }

    //初始化
    private void init() {
        begin.setOnClickListener(this);
        pause.setOnClickListener(this);
        delete.setOnClickListener(this);
        ifexist.setOnClickListener(this);
    }

    //点击事件
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            //开始下载
            case R.id.begin:
                if (isDown){
                    Toast.makeText(getApplicationContext(),"正在下载",Toast.LENGTH_SHORT).show();
                } else if(lists.size()>0){
                    //恢复下载
                    isDown = true;
                    lists.clear();
                    for (int i = 0; i <N; i++) {
                        log("恢复"+i);
                        SingleDownloadThread thread = new SingleDownloadThread(url,i);
                        lists.add(thread);
                        thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }

                }else{
                    file.delete();
                    builder = new StringBuilder();
                    tv_result.setText("");
                    new DownloadThread().start();
                }
                break;

            //暂停
            case R.id.pause:
                isDown = false;
                for (SingleDownloadThread entity : lists) {
                    entity.cancel(true);
                }
            break;

            //删除
            case R.id.delete:
                if (!isDown && lists.size() == 0){
                    file.delete();
                }
            break;

            //文件是否存在
            case R.id.ifexist:
                if (file.exists()){
                    ifexist.setText("文件是否存在：存在");
                }else {
                    ifexist.setText("文件是否存在：不存在");
                }
            break;
        }
    }

    private Handler handler = new Handler(){
        //0下载失败  1下载成功  2更新进度
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 0:
                    havedown.setText("当前已下载：" + down);
                    totalSize.setText("文件总大小：" + allsize);
                    if(allsize == down){
                        Toast.makeText(getApplicationContext(),"下载成功",Toast.LENGTH_SHORT).show();
                        isDown = false;
                        lists.clear();
                    }
//                    tv_result.setText(builder.toString());
                break;
            }
        }
    };

    //下载线程
    class DownloadThread extends Thread{
        @Override
        public void run() {
            try {
                isDown = true;
                down = 0;
                url = new URL("http://msoftdl.360.cn/mobilesafe/shouji360/360safesis/360StrongBox_1.1.0.1013.apk");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                allsize = connection.getContentLength();
                if (connection.getResponseCode() == 200 && allsize > -1){
                    RandomAccessFile randomAccessFile = new RandomAccessFile(file,"rw");
                    randomAccessFile.setLength(allsize);

                    //分成n部分下载
                    int size = allsize/N;
                    for (int i = 0; i <N; i++) {
                        int begin = size * i;
                        int end = size * (i+1)-1 ;
                        if (i == N-1) {
                            end = allsize;
                        }
                        Entity entity = new Entity(begin,end);
                        map.put(i, entity);
                        SingleDownloadThread thread = new SingleDownloadThread(url,i);
                        lists.add(thread);
                        thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                }
            } catch (Exception e) {
                isDown = false;
            }
        }
    }

    //单一线程下载
    class SingleDownloadThread extends AsyncTask<Void,Void,Void>{
        private URL url;
        private int id;
        private Entity entity;

        public SingleDownloadThread(URL url,int id){
            this.url = url;
            this.id = id;
            this.entity = map.get(id);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if(entity.begin > entity.end){
                    return null;
                }
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Range","bytes="+entity.begin+"-"+entity.end);
                InputStream input = connection.getInputStream();
                RandomAccessFile randomAccessFile = new RandomAccessFile(file,"rw");
                randomAccessFile.seek(entity.begin);
                byte[] bytes = new byte[500000];
                int len = 0;
                while (isDown && (len = input.read(bytes)) != -1){
                    log(len);
                    randomAccessFile.write(bytes, 0, len);
                    entity.begin = entity.begin + len;
                    updateProgress(len);
                    handler.sendEmptyMessage(0);
                }
                input.close();
                randomAccessFile.close();
            } catch (IOException e) {
                builder.append(id + "部分下载失败\n");
            }
            return null;
        }
    }

    //更新进度
    synchronized public void updateProgress(int add){
        down = down + add;
    }


    public void log(Object s){
        Log.e("tag",s+"");
    }
}
