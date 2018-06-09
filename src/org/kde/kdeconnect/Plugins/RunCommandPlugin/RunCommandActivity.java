/*
 * Copyright 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * Copyright 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RunCommandActivity extends AppCompatActivity {

    private String deviceId;
    private final RunCommandPlugin.CommandsChangedCallback commandsChangedCallback = this::updateView;

    private void updateView() {
        BackgroundService.RunCommand(this, service -> {

            final Device device = service.getDevice(deviceId);
            final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin == null) {
                Log.e("RunCommandActivity", "device has no runcommand plugin!");
                return;
            }

            runOnUiThread(() -> {
                ListView view = (ListView) findViewById(R.id.runcommandslist);

                final ArrayList<ListAdapter.Item> commandItems = new ArrayList<>();
                for (JSONObject obj : plugin.getCommandList()) {
                    try {
                        commandItems.add(new CommandEntry(obj.getString("name"),
                                obj.getString("command"), obj.getString("key")));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                Collections.sort(commandItems, (lhs, rhs) -> {
                    String lName = ((CommandEntry) lhs).getName();
                    String rName = ((CommandEntry) rhs).getName();
                    return lName.compareTo(rName);
                });

                ListAdapter adapter = new ListAdapter(RunCommandActivity.this, commandItems);

                view.setAdapter(adapter);
                view.setOnItemClickListener((adapterView, view1, i, l) -> {
                    CommandEntry entry = (CommandEntry) commandItems.get(i);
                    plugin.runCommand(entry.getKey());
                });


                TextView explanation = (TextView) findViewById(R.id.addcomand_explanation);
                String text = getString(R.string.addcommand_explanation);
                if (!plugin.canAddCommand()) {
                    text += "\n" + getString(R.string.addcommand_explanation2);
                }
                explanation.setText(text);
                explanation.setVisibility(commandItems.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);
        setContentView(R.layout.activity_runcommand);

        deviceId = getIntent().getStringExtra("deviceId");

        boolean canAddCommands = BackgroundService.getInstance().getDevice(deviceId).getPlugin(RunCommandPlugin.class).canAddCommand();

        FloatingActionButton addCommandButton = (FloatingActionButton) findViewById(R.id.add_command_button);
        addCommandButton.setVisibility(canAddCommands ? View.VISIBLE : View.GONE);

        addCommandButton.setOnClickListener(view -> BackgroundService.RunCommand(RunCommandActivity.this, service -> {

            final Device device = service.getDevice(deviceId);
            final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin == null) {
                Log.e("RunCommandActivity", "device has no runcommand plugin!");
                return;
            }

            plugin.sendSetupPacket();

            AlertDialog dialog = new AlertDialog.Builder(RunCommandActivity.this)
                    .setTitle(R.string.add_command)
                    .setMessage(R.string.add_command_description)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            dialog.show();

        }));

        updateView();
    }

    @Override
    protected void onResume() {
        super.onResume();

        BackgroundService.RunCommand(this, service -> {

            final Device device = service.getDevice(deviceId);
            final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin == null) {
                Log.e("RunCommandActivity", "device has no runcommand plugin!");
                return;
            }
            plugin.addCommandsUpdatedCallback(commandsChangedCallback);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        BackgroundService.RunCommand(this, service -> {

            final Device device = service.getDevice(deviceId);
            final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin == null) {
                Log.e("RunCommandActivity", "device has no runcommand plugin!");
                return;
            }
            plugin.removeCommandsUpdatedCallback(commandsChangedCallback);
        });
    }
}
