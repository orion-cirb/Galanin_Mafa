# Galanin_Mafa

* **Developed for:** Laure
* **Team:** Rouach
* **Date:** May 2023
* **Software:** Fiji

### Images description

3D images taken with a x60 objective

2 channels:
  1. *Alexa Fluor 488:* Galanin cells
  2. *Alexa Fluor 642:* MafA cells

### Plugin description

* Detect Galanin and MafA cells with Cellpose
* Colocalize Galanin cells with MafA cells and MafA cells with Galanin cells
* Compute background noise for each channel
* For each cell, compute its volume and intensity/background-corrected intensity in each channel in which it appears

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **Cellpose** conda environment + *cyto2* model

### Version history

Version 1 released on May 5, 2023.

