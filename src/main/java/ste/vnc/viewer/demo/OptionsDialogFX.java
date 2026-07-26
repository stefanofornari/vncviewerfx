/* Copyright (C) 2026 VNC Viewer Contributors
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */

package ste.vnc.viewer.demo;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.tigervnc.rfb.BoolParameter;
import com.tigervnc.rfb.Configuration;
import com.tigervnc.rfb.IntParameter;
import com.tigervnc.rfb.StringParameter;
import com.tigervnc.rfb.VoidParameter;

/**
 * Simple JavaFX options dialog for the most common VNC viewer parameters.
 */
class OptionsDialogFX {

  private final Window owner;

  public OptionsDialogFX(Window owner) {
    this.owner = owner;
  }

  public void show() {
    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.setTitle("Options");

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(10));

    int row = 0;

    // FullColour
    BoolParameter fullColour = getBoolParam("FullColour");
    CheckBox fullColourBox = new CheckBox("Use full colour");
    if (fullColour != null)
      fullColourBox.setSelected(fullColour.getValue());
    grid.add(fullColourBox, 0, row++, 2, 1);

    // LowColorLevel
    IntParameter lowColorLevel = getIntParam("LowColorLevel");
    ChoiceBox<String> lowColorChoice = new ChoiceBox<>();
    lowColorChoice.getItems().addAll("Very Low (8 colours)", "Low (64 colours)", "Medium (256 colours)");
    if (lowColorLevel != null)
      lowColorChoice.getSelectionModel().select(lowColorLevel.getValue());
    grid.add(new Label("Low colour level:"), 0, row);
    grid.add(lowColorChoice, 1, row++);

    // PreferredEncoding
    StringParameter preferredEncoding = getStringParam("PreferredEncoding");
    ChoiceBox<String> encodingChoice = new ChoiceBox<>();
    encodingChoice.getItems().addAll("Tight", "ZRLE", "Hextile", "Raw");
    if (preferredEncoding != null) {
      String enc = preferredEncoding.getValue();
      if (enc != null)
        encodingChoice.getSelectionModel().select(enc);
    }
    grid.add(new Label("Preferred encoding:"), 0, row);
    grid.add(encodingChoice, 1, row++);

    // ViewOnly
    BoolParameter viewOnly = getBoolParam("ViewOnly");
    CheckBox viewOnlyBox = new CheckBox("View only (do not send input)");
    if (viewOnly != null)
      viewOnlyBox.setSelected(viewOnly.getValue());
    grid.add(viewOnlyBox, 0, row++, 2, 1);

    // UseLocalCursor
    BoolParameter useLocalCursor = getBoolParam("UseLocalCursor");
    CheckBox localCursorBox = new CheckBox("Render cursor locally");
    if (useLocalCursor != null)
      localCursorBox.setSelected(useLocalCursor.getValue());
    grid.add(localCursorBox, 0, row++, 2, 1);

    // ScalingFactor
    StringParameter scalingFactor = getStringParam("ScalingFactor");
    TextField scalingField = new TextField(scalingFactor != null ? scalingFactor.getValue() : "100");
    grid.add(new Label("Scaling factor (%):"), 0, row);
    grid.add(scalingField, 1, row++);

    // CompressLevel
    IntParameter compressLevel = getIntParam("CompressLevel");
    TextField compressField = new TextField(compressLevel != null ? Integer.toString(compressLevel.getValue()) : "1");
    grid.add(new Label("Compression level (0-9):"), 0, row);
    grid.add(compressField, 1, row++);

    // QualityLevel
    IntParameter qualityLevel = getIntParam("QualityLevel");
    TextField qualityField = new TextField(qualityLevel != null ? Integer.toString(qualityLevel.getValue()) : "8");
    grid.add(new Label("JPEG quality (0-9):"), 0, row);
    grid.add(qualityField, 1, row++);

    // Buttons
    Button okButton = new Button("OK");
    Button cancelButton = new Button("Cancel");
    HBox buttonBox = new HBox(10, okButton, cancelButton);
    buttonBox.setPadding(new Insets(10, 0, 0, 0));

    VBox root = new VBox(10, grid, buttonBox);
    root.setPadding(new Insets(10));

    okButton.setOnAction(e -> {
      apply(fullColourBox, lowColorChoice, encodingChoice, viewOnlyBox, localCursorBox,
            scalingField, compressField, qualityField);
      stage.close();
    });
    cancelButton.setOnAction(e -> stage.close());

    stage.setScene(new Scene(root));
    stage.sizeToScene();
    stage.showAndWait();
  }

  private void apply(CheckBox fullColourBox, ChoiceBox<String> lowColorChoice,
                     ChoiceBox<String> encodingChoice, CheckBox viewOnlyBox,
                     CheckBox localCursorBox, TextField scalingField,
                     TextField compressField, TextField qualityField) {
    setBoolParam("FullColour", fullColourBox.isSelected());
    setIntParam("LowColorLevel", lowColorChoice.getSelectionModel().getSelectedIndex());
    setStringParam("PreferredEncoding", encodingChoice.getSelectionModel().getSelectedItem());
    setBoolParam("ViewOnly", viewOnlyBox.isSelected());
    setBoolParam("UseLocalCursor", localCursorBox.isSelected());
    setStringParam("ScalingFactor", scalingField.getText());
    setIntParam("CompressLevel", parseInt(compressField.getText()));
    setIntParam("QualityLevel", parseInt(qualityField.getText()));
  }

  private static BoolParameter getBoolParam(String name) {
    VoidParameter p = Configuration.getParam(name);
    return (p instanceof BoolParameter) ? (BoolParameter)p : null;
  }

  private static IntParameter getIntParam(String name) {
    VoidParameter p = Configuration.getParam(name);
    return (p instanceof IntParameter) ? (IntParameter)p : null;
  }

  private static StringParameter getStringParam(String name) {
    VoidParameter p = Configuration.getParam(name);
    return (p instanceof StringParameter) ? (StringParameter)p : null;
  }

  private static void setBoolParam(String name, boolean value) {
    BoolParameter p = getBoolParam(name);
    if (p != null)
      p.setParam(value);
  }

  private static void setIntParam(String name, int value) {
    IntParameter p = getIntParam(name);
    if (p != null)
      p.setParam(value);
  }

  private static void setStringParam(String name, String value) {
    StringParameter p = getStringParam(name);
    if (p != null && value != null)
      p.setParam(value);
  }

  private static int parseInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
