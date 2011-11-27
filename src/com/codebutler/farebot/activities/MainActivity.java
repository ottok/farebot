/*
 * MainActivity.java
 *
 * Copyright (C) 2011 Eric Butler
 *
 * Authors:
 * Eric Butler <eric@codebutler.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.codebutler.farebot.activities;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.codebutler.farebot.ExportHelper;
import com.codebutler.farebot.R;
import com.codebutler.farebot.Utils;
import com.codebutler.farebot.mifare.Card;
import com.codebutler.farebot.provider.CardDBHelper;
import com.codebutler.farebot.provider.CardProvider;
import com.codebutler.farebot.provider.CardsTableColumns;
import com.codebutler.farebot.transit.TransitIdentity;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends ListActivity
{
    private static final int SELECT_FILE = 1;

    private static final String SD_EXPORT_PATH = Environment.getExternalStorageDirectory() + "/FareBot-Export.xml";

    private Map<String, TransitIdentity> mDataCache;

    @Override
    protected void onCreate (Bundle bundle)
    {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);

        mDataCache = new HashMap<String, TransitIdentity>();

        Cursor cursor = CardDBHelper.createCursor(this);
        startManagingCursor(cursor);

        setListAdapter(new ResourceCursorAdapter(this, android.R.layout.simple_list_item_2, cursor) {
            @Override
            public void bindView (View view, Context context, Cursor cursor) {
                int    type    = cursor.getInt(cursor.getColumnIndex(CardsTableColumns.TYPE));
                String serial  = cursor.getString(cursor.getColumnIndex(CardsTableColumns.TAG_SERIAL));
                Date scannedAt = new Date(cursor.getLong(cursor.getColumnIndex(CardsTableColumns.SCANNED_AT)));

                if (!mDataCache.containsKey(serial)) {
                    String data = cursor.getString(cursor.getColumnIndex(CardsTableColumns.DATA));
                    try {
                        mDataCache.put(serial, Card.fromXml(data).parseTransitIdentity());
                    } catch (Exception ex) {
                        mDataCache.put(serial, new TransitIdentity("Error: " + ex, null));
                    }
                }

                TransitIdentity identity = mDataCache.get(serial);
                
                TextView textView1 = (TextView) view.findViewById(android.R.id.text1);
                TextView textView2 = (TextView) view.findViewById(android.R.id.text2);

                if (identity != null) {
                    if (identity.getSerialNumber() != null) {
                        textView1.setText(String.format("%s: %s", identity.getName(), identity.getSerialNumber()));
                    } else {
                        textView1.setText(identity.getName());
                    }
                    textView2.setText(getString(R.string.scanned_at_format, SimpleDateFormat.getTimeInstance(DateFormat.SHORT).format(scannedAt), SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(scannedAt)));
                } else {
                    textView1.setText(getString(R.string.unknown_card));
                    textView2.setText(String.format("%s - %s", Card.CardType.values()[type].toString(), serial));
                }

                textView2.setText(String.format("%s - %s", Card.CardType.values()[type].toString(), serial));
            }
        });

        registerForContextMenu(getListView());
    }

    @Override
    public void onCreateContextMenu (ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        getMenuInflater().inflate(R.menu.card_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected (MenuItem item)
    {
        if (item.getItemId() == R.id.delete_card) {
            long id = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).id;
            Uri uri = ContentUris.withAppendedId(CardProvider.CONTENT_URI_CARD, id);
            getContentResolver().delete(uri, null, null);
            return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick (ListView l, View v, int position, long id)
    {
        Uri uri = ContentUris.withAppendedId(CardProvider.CONTENT_URI_CARD, id);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    @Override
    public boolean onCreateOptionsMenu (Menu menu)
    {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item)
    {
        try {
            if (item.getItemId() == R.id.about) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://codebutler.github.com/farebot")));
                return true;

            } else if (item.getItemId() == R.id.import_clipboard) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                onCardsImported(ExportHelper.importCardsXml(this, clipboard.getText().toString()));
                return true;

            } else if (item.getItemId() == R.id.import_file) {
                Uri uri = Uri.fromFile(Environment.getExternalStorageDirectory());
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.setType("application/xml");
                startActivityForResult(Intent.createChooser(i, "Select File"), SELECT_FILE);
                return true;

            } else if (item.getItemId() == R.id.import_sd) {
                String xml = FileUtils.readFileToString(new File(SD_EXPORT_PATH));
                onCardsImported(ExportHelper.importCardsXml(this, xml));
                return true;

            } else if (item.getItemId() == R.id.copy_xml) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setText(ExportHelper.exportCardsXml(this));
                Toast.makeText(this, "Copied to clipboard.", 5).show();
                return true;

            } else if (item.getItemId() == R.id.share_xml) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, ExportHelper.exportCardsXml(this));
                startActivity(intent);
                return true;

            } else if (item.getItemId() == R.id.save_xml) {
                String xml = ExportHelper.exportCardsXml(this);
                File file = new File(SD_EXPORT_PATH);
                FileUtils.writeStringToFile(file, xml, "UTF-8");
                Toast.makeText(this, "Wrote FareBot-Export.xml to USB Storage.", 5).show();
                return true;

            } else if (item.getItemId() == R.id.prefs) {
                startActivity(new Intent(this, FareBotPreferenceActivity.class));
            }
        } catch (Exception ex) {
            Utils.showError(this, ex);
        }
        return false;
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        try {
            if (resultCode == RESULT_OK && requestCode == SELECT_FILE) {
                Uri uri = data.getData();
                String xml = org.apache.commons.io.FileUtils.readFileToString(new File(uri.getPath()));
                onCardsImported(ExportHelper.importCardsXml(this, xml));
            }
        } catch (Exception ex) {
            Utils.showError(this, ex);
        }
    }

    private void onCardsImported (Uri[] uris)
    {
        ((ResourceCursorAdapter) getListAdapter()).notifyDataSetChanged();
        if (uris.length == 1) {
            Toast.makeText(this, "Card imported!", 5).show();
            startActivity(new Intent(Intent.ACTION_VIEW, uris[0]));
        } else {
            Toast.makeText(this, "Cards Imported: " + uris.length, 5).show();
        }
    }
}
