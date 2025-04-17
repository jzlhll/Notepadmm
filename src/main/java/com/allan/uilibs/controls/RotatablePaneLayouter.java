package com.allan.uilibs.controls;

import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

public class RotatablePaneLayouter extends Region {
    private Pane child;

    public void addChild(Pane child) {
        getChildren().add(child);
        this.child = child;

        // make sure layout gets invalidated when the child orientation changes
        child.rotateProperty().addListener((observable, oldValue, newValue) -> requestLayout());
    }

    @Override
    protected void layoutChildren() {
        System.out.println("ratable: layoutChildren " + child.getRotate());
        // set fit sizes:
        //resize child to fit into RotatablePane and correct movement caused by resizing if necessary
        if ((child.getRotate() == 90)||(child.getRotate() == 270)) {
            //vertical
            child.resize( getHeight(), getWidth() ); //exchange width and height
            // and relocate to correct movement caused by resizing
            double delta = (getWidth() - getHeight()) / 2;
            child.relocate(delta,-delta);
        } else {
            //horizontal
            child.resize( getWidth(), getHeight() ); //keep width and height
            //with 0° or 180° resize does no movement to be corrected
            child.relocate(0,0);
        }
    }
}
