package com.gmail.umedutakaaki.mugenkairo;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        main_view = ((MainView) findViewById(R.id.main_view));

    }

    private void set_arrow_del_enabled() {
        if(main_view.arrow_count() > 2) {
            item_arrow_del.setEnabled(true);
        }else{
            item_arrow_del.setEnabled(false);
        }

    }

    MainView main_view;
    MenuItem item_save;
    MenuItem item_arrow_add;
    MenuItem item_arrow_del;
    MenuItem item_composed;
    MenuItem item_masked;
    MenuItem item_mask;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        item_save = menu.findItem(R.id.action_save);
        item_arrow_add = menu.findItem(R.id.action_arrow_add);
        item_arrow_del = menu.findItem(R.id.action_arrow_del);
        item_composed = menu.findItem(R.id.action_composed);
        item_masked = menu.findItem(R.id.action_masked);
        item_mask = menu.findItem(R.id.action_mask);
        return true;
    }

    private void show_message(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch(id) {
            case R.id.action_save:
                save_image();
                return true;
            case R.id.action_load:
                load_image();
                return true;
            case R.id.action_composed:
            case R.id.action_masked:
            case R.id.action_mask:
                if(!item.isChecked()) {
                    main_view.set_view_mode(id);
                    item.setChecked(true);
                }
                return true;
            case R.id.action_arrow_add:
                main_view.add_arrow();
                set_arrow_del_enabled();
                return true;
            case R.id.action_arrow_del:
                main_view.del_arrow();;
                set_arrow_del_enabled();
                return true;
            case R.id.action_help:
                Intent intent = new Intent(this, HelpActivity.class);
                startActivity(intent);
                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    private void save_image() {
        Bitmap bmp = main_view.compose_result(true);
        String ext, mime_type;
        if(main_view.get_view_mode() == R.id.action_composed){
            ext = ".jpg";
            mime_type = "image/jpeg";
        }else {
            ext = ".png";
            mime_type = "image/png";
        }
        if(bmp != null) {
            try {
                String save_dir = getString(R.string.name_save_dir);
                File root = Environment.getExternalStorageDirectory();
                File pictures = new File(root.getPath() + "/Pictures");
                if(pictures.isDirectory()){
                    root = new File(pictures.getPath() + "/" + save_dir);
                }else if(!pictures.exists()) {
                    pictures.mkdir();
                    root = new File(pictures.getPath() + "/" + save_dir);
                }else{
                    root = new File(root.getPath() + "/" + save_dir);
                }

                if(!root.isDirectory()){
                    if(root.exists()){
                        int i = 0;
                        File tmp_root;
                        while(true){
                            tmp_root = new File(root.getPath() + "_" + i);
                            if(tmp_root.isDirectory()) {
                                root = tmp_root;
                                break;
                            }
                            if(!tmp_root.exists()){
                                root = tmp_root;
                                root.mkdir();
                                break;
                            }
                            i ++;
                        }
                    }else {
                        root.mkdir();
                    }
                }
                SimpleDateFormat filename_format = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String filename_base = filename_format.format(new Date());
                String filename = filename_base + ext;
                File file_to_save = new File(root, filename);
                int i = 0;
                while (file_to_save.exists()) {
                    filename = filename_base + "_" + i + ext;
                    file_to_save = new File(root, filename);
                    i++;
                }
                String filepath = file_to_save.toString();
                FileOutputStream output = new FileOutputStream(filepath);
                if(ext == ".jpg") {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, output);
                }else{
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                output.close();


                ContentValues values = new ContentValues();
                ContentResolver contentResolver = getContentResolver();
                values.put(MediaStore.Images.Media.MIME_TYPE, mime_type);
                values.put(MediaStore.Images.Media.TITLE, filename);
                values.put("_data", filepath);
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                show_message(getText(R.string.saved_message) + filename);
            }catch(IOException e) {
                Log.e("Error", e.toString());
                show_message(getString(R.string.error_message) + e.toString());
            }

        }
    }

    private void load_image() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RESULT_PICK_IMAGE_FILE);
    }

    final int RESULT_PICK_IMAGE_FILE = 123;
    int x = 0;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        switch (requestCode) {
            case RESULT_PICK_IMAGE_FILE:
                if (resultCode == RESULT_OK) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = false;
                    Bitmap bmp = null;
                    Uri selectedImageURI = intent.getData();

                    if(selectedImageURI == null) {
                        return;
                    }

                    InputStream input = null;
                    try {
                        input = getContentResolver().openInputStream(selectedImageURI);
                        bmp = BitmapFactory.decodeStream(input, null, options);

                    } catch (FileNotFoundException e) {
                        Log.e("Error", e.toString());
                        show_message(getString(R.string.error_message) + e.toString());

                    }

                    if(bmp != null) {
                        main_view.set_target_image(bmp);

                        item_save.setEnabled(true);
                        item_arrow_add.setEnabled(true);
                        item_composed.setEnabled(true);
                        item_masked.setEnabled(true);
                        item_mask.setEnabled(true);
                        item_composed.setChecked(true);
                        main_view.set_view_mode(R.id.action_composed);
                        set_arrow_del_enabled();
                    }
                }
                break;
            default:
                break;
        }
    }

}
