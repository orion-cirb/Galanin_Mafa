package Galanin_Mafa_Tools;

import mcib3d.geom2.Object3DInt;

/**
 * @author Orion-CIRB
 */
public class Cell {
    
    // Galanin cell
    private Object3DInt galCell;
    // Mafa cell
    private Object3DInt mafaCell;
    

    public Cell() {
        this.galCell = null;
        this.mafaCell = null;
    }
    
    public Object3DInt getGalCell() {
        return galCell;
    }
    
    public Object3DInt getMafaCell() {
        return mafaCell;
    }
    
    public void setGalCell(Object3DInt galCell) {
        this.galCell = galCell;
    }
    
    public void setMafaCell(Object3DInt mafaCell) {
        this.mafaCell = mafaCell;
    }
    
}
