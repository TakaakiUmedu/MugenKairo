package com.gmail.umedutakaaki.mugenkairo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        main_view = ((MainView) findViewById(R.id.main_view));

        Intent intent = getIntent();
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_EDIT) || action.equals(Intent.ACTION_VIEW)) {
            Bundle bundle = intent.getExtras();
            Uri uri = intent.getData();

            if (uri != null) {
                main_view.load_source_image(uri);
            }
        }
    }


    private void set_save_enabled() {
        if (main_view.get_view_mode() != MainView.VIEW_MODE_ORIGINAL && main_view.image_loaded()) {
            item_save.setEnabled(true);
            item_share.setEnabled(true);
        } else {
            item_save.setEnabled(false);
            item_share.setEnabled(false);
        }

    }

    private void set_arrow_del_enabled() {
        if (main_view.arrow_count() > 2) {
            item_arrow_del.setEnabled(true);
        } else {
            item_arrow_del.setEnabled(false);
        }
    }

    MainView main_view;
    MenuItem item_save;
    MenuItem item_share;
    MenuItem item_arrow_add;
    MenuItem item_arrow_del;
    MenuItem item_view_mode;
    MenuItem item_original;
    MenuItem item_composed;
    MenuItem item_masked;
    MenuItem item_mask;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        item_save = menu.findItem(R.id.action_save);
        item_share = menu.findItem(R.id.action_share);
        item_arrow_add = menu.findItem(R.id.action_arrow_add);
        item_arrow_del = menu.findItem(R.id.action_arrow_del);
        item_view_mode = menu.findItem(R.id.action_view_mode);
        item_original = menu.findItem(R.id.action_original);
        item_composed = menu.findItem(R.id.action_composed);
        item_masked = menu.findItem(R.id.action_masked);
        item_mask = menu.findItem(R.id.action_mask);
        menu_initialized = true;
        if(waiting_for_menu_initialization) {
        enable_menu_items();
        }
        return true;
    }
    boolean menu_initialized = false;
    boolean waiting_for_menu_initialization = false;

    public void show_message(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void show_message_short(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_save:
                save_image();
                return true;
            case R.id.action_load:
                load_image();
                return true;
            case R.id.action_original:
            case R.id.action_composed:
            case R.id.action_masked:
            case R.id.action_mask:
                if (!item.isChecked()) {
                    main_view.set_view_mode(id);
                    item.setChecked(true);
                    set_save_enabled();
                }
                return true;
            case R.id.action_arrow_add:
                main_view.add_arrow();
                set_arrow_del_enabled();
                return true;
            case R.id.action_arrow_del:
                main_view.del_arrow();
                ;
                set_arrow_del_enabled();
                return true;
            case R.id.action_help:
                Intent intent = new Intent(this, HelpActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_exit:
                finish();
                return true;
            case R.id.action_share:
                share_image();
                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    private void share_image(){
        MainView.SavedData saved_data = main_view.save_image();
        if(saved_data != null) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, "#" + getString(R.string.app_name).replaceAll(" ", "") + " ");
            intent.putExtra(Intent.EXTRA_STREAM, saved_data.uri);
            intent.setType(saved_data.mime_type);
//            startActivity(intent);
            startActivity(Intent.createChooser(intent, getString(R.string.choose_message)));
        }
    }

    private void save_image() {
        main_view.save_image();
    }

    void load_image() {
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
                    Uri uri = intent.getData();

                    if (uri == null) {
                        return;
                    } else {
                        main_view.load_source_image(uri);
                    }
                }
                break;
            default:
                break;
        }
    }
    public void enable_menu_items(){
        if(!menu_initialized){
            waiting_for_menu_initialization = true;
            return;
        }
        set_save_enabled();
        item_arrow_add.setEnabled(true);
        item_view_mode.setEnabled(true);
        item_original.setEnabled(true);
        item_composed.setEnabled(true);
        item_masked.setEnabled(true);
        item_mask.setEnabled(true);
        item_composed.setChecked(true);
        set_arrow_del_enabled();

    }

    @Override
    public void onDestroy() {
        main_view.destroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        main_view.save_state(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        main_view.restore_state(savedInstanceState);

    }


}
