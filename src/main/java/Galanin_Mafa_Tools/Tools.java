package Galanin_Mafa_Tools;


import Galanin_Mafa_Tools.Cellpose.CellposeSegmentImgPlusAdvanced;
import Galanin_Mafa_Tools.Cellpose.CellposeTaskSettings;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import io.scif.DependencyException;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.ImageIcon;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DComputation;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.geom2.measurementsPopulation.PairObjects3DInt;
import mcib3d.image3d.ImageHandler;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;


/**
 * @author Orion-CIRB
 */
public class Tools {
    private final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    public Calibration cal = new Calibration();
    public double pixVol= 0;
    private final String helpUrl = "https://github.com/orion-cirb/Galanin_Mafa";
    
    private CLIJ2 clij2 = CLIJ2.getInstance();
    
    // Nuclei and cells detection with Cellpose
    private final String cellposeEnvDirPath = IJ.isWindows()? System.getProperty("user.home")+"\\miniconda3\\envs\\CellPose" : "/opt/miniconda3/envs/cellpose";
    public final String cellposeModelPath = IJ.isWindows()? System.getProperty("user.home")+"\\.cellpose\\models\\" : "";
    public final String cellposeModel = "cyto2";
    
    public int cellposeCellDiameter = 60;
    public double minCellVol = 600;
    public double maxCellVol = 15000;
    private int roiBgSize = 100;
    
    
    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }

    /**
     * Find images extension
     * @param imagesFolder
     * @return 
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        File[] files = imagesFolder.listFiles();
        for (File file: files) {
            if(file.isFile()) {
                String fileExt = FilenameUtils.getExtension(file.getName());
                switch (fileExt) {
                   case "nd" :
                       ext = fileExt;
                       break;
                   case "nd2" :
                       ext = fileExt;
                       break;
                    case "czi" :
                       ext = fileExt;
                       break;
                    case "lif"  :
                        ext = fileExt;
                        break;
                    case "ics2" :
                        ext = fileExt;
                        break;
                    case "tif" :
                        ext = fileExt;
                        break;
                    case "tiff" :
                        ext = fileExt;
                        break;
                }
            } else if (file.isDirectory() && !file.getName().contains("Results")) {
                ext = findImageType(file);
                if (! ext.equals(""))
                    break;
            }
        }
        return(ext);
    }
    
    /**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    /**
     * Find image calibration
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    /**
     * Find channels name and None to end of list
     * @param imageName
     * @param meta
     * @param reader
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs+1];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n).toString();
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelFluor(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelFluor(0, n).toString();
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = meta.getChannelEmissionWavelength(0, n).value().toString();
                break;    
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = meta.getChannelEmissionWavelength(0, n).value().toString();
                break; 
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        channels[chs] = "None";
        return(channels);     
    }
    
    
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 150, 0);
        gd.addImage(icon);
        String[] channelNames = {"Galanin", "Mafa"};
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames: channelNames) {
            gd.addChoice(chNames + ": ", chs, chs[index]);
            index++;
        }
        gd.addMessage("Background detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Roi size : ", roiBgSize);
        gd.addMessage("Cells detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min cell volume (µm3): ", minCellVol);
        gd.addNumericField("Max cell volume (µm3): ", maxCellVol);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel depth (µm):", cal.pixelDepth);
        gd.addHelp(helpUrl);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        if(gd.wasCanceled())
            chChoices = null;
        roiBgSize = (int)gd.getNextNumber();
        minCellVol = gd.getNextNumber();
        maxCellVol = gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        
        return(chChoices);
    }
    
    
    /**
     * Flush and close an image
     */
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Look for all 3D cells in a Z-stack: 
     * - apply CellPose in 2D slice by slice 
     * - let CellPose reconstruct cells in 3D using the stitch threshold parameters
     */
    public Objects3DIntPopulation cellposeDetection(ImagePlus img) throws IOException{
        ImagePlus imgDup = new Duplicator().run(img);
        // Define CellPose settings
        CellposeTaskSettings settings = new CellposeTaskSettings(cellposeModel, 1, cellposeCellDiameter, cellposeEnvDirPath);
        settings.setStitchThreshold(0.75);
        settings.useGpu(true);
        // Run CellPose
        CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgDup);
        ImagePlus imgOut = cellpose.run().resize(img.getWidth(), img.getHeight(), "none");
        imgOut.setCalibration(cal);
        // Get cells as a population of objects and filter them
        ImageHandler imgH = ImageHandler.wrap(imgOut);
        Objects3DIntPopulation pop = new Objects3DIntPopulation(imgH);
        System.out.println(pop.getNbObjects() + " detections");
        removeTouchingBorder(pop, imgOut);
        popFilterSize​(pop, minCellVol, maxCellVol);
        System.out.println(pop.getNbObjects() + " detections remaining after filtering");
        flush_close(imgOut);
        imgH.closeImagePlus();
        return(pop);
    }
    
    /**
     * Remove object with size < min and size > max in microns
     * @param pop
     * @param min
     * @param max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
    
    /**
     * Remove object with only one plan
     * @param pop
     */
    public void popFilterOneZ(Objects3DIntPopulation pop) {
        pop.getObjects3DInt().removeIf(p -> (p.getObject3DPlanes().size() == 1));
        pop.resetLabels();
    }
    
    /**
     * Remove object touching border image
     */
    public void removeTouchingBorder(Objects3DIntPopulation pop, ImagePlus img) {
        ImageHandler imh = ImageHandler.wrap(img);
        pop.getObjects3DInt().removeIf(p -> (new Object3DComputation(p).touchBorders(imh, false)));
        pop.resetLabels();
    }
    
    /**
     * Find in Cells if coloc have been done for is cell
     */
    private boolean findCell(ArrayList<Cell> cells, float label) {
        boolean found = false;
        for (Cell cell : cells) {
            if (cell.getMafaCell() != null) {
                found = (cell.getMafaCell().getLabel() == label);
                if (found)
                   break;
            }
        }
        return(found);
    }
    
    /**
     * Find coloc objects in gal colocalized with mafa
     * @param pop1
     * @param pop2
     * @param cellType
     */
    public ArrayList<Cell> findColocPop (ArrayList<Cell> cells, Objects3DIntPopulation pop1, Objects3DIntPopulation pop2, String cellType) {
        double pourc = 0.55;
        if (pop1.getNbObjects() > 0 && pop2.getNbObjects() > 0) {
            MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(pop1, pop2);
            for (int i = 1; i <= pop1.getNbObjects(); i++) {
                Object3DInt obj1 = pop1.getObjectByLabel(i);
                Cell cell = new Cell();
                if (cellType.equals("gal"))
                    cell.setGalCell(obj1);
                else 
                    if (findCell(cells, obj1.getLabel()))
                        break;    
                    else 
                        cell.setMafaCell(obj1);
                List<PairObjects3DInt> list = coloc.getPairsObject1(obj1.getLabel(), true);
                if (!list.isEmpty()) {
                    for (int j = 0; j < list.size(); j++) {
                        Object3DInt obj2 = list.get(j).getObject3D2();
                        if (list.get(j).getPairValue() > obj2.size()*pourc) {
                            if (cellType.equals("gal"))
                                cell.setMafaCell(obj2);
                            else
                                cell.setGalCell(obj2);
                            break;
                        }
                    }
                }
                cells.add(cell);
            }
        }
        return(cells);
    }

    
    
    /**
     * Save detected cells and foci in image
     */
    public void drawResults(Objects3DIntPopulation galPop, Objects3DIntPopulation mafaPop, ImagePlus imgGal, ImagePlus imgMafa, String imageName, String outDir) {

        ImageHandler imgGalObj = ImageHandler.wrap(imgGal).createSameDimensions();
        ImageHandler imgMafaObj = imgGalObj.createSameDimensions();
        galPop.drawInImage(imgGalObj);
        mafaPop.drawInImage(imgMafaObj);

        ImagePlus[] imgColors1 = {imgGalObj.getImagePlus(), null, null, imgGal};
        ImagePlus imgObjects1 = new RGBStackMerge().mergeHyperstacks(imgColors1, true);
        imgObjects1.setCalibration(imgGal.getCalibration());
        FileSaver ImgObjectsFile1 = new FileSaver(imgObjects1);
        ImgObjectsFile1.saveAsTiff(outDir + imageName + "_Galanin_cells.tif");
        flush_close(imgObjects1);
        ImagePlus[] imgColors2 = {imgMafaObj.getImagePlus(), null, null, imgMafa};
        ImagePlus imgObjects2 = new RGBStackMerge().mergeHyperstacks(imgColors2, true);
        imgObjects1.setCalibration(imgGal.getCalibration());
        FileSaver ImgObjectsFile2 = new FileSaver(imgObjects2);
        ImgObjectsFile2.saveAsTiff(outDir + imageName + "_Mafa_cells.tif");
        flush_close(imgObjects2);
        imgGalObj.closeImagePlus();
        imgMafaObj.closeImagePlus();
    }
    
    
    /**
     * Do Z projection
     * @param img
     * @param param
     * @return 
     */
    public ImagePlus doZProjection(ImagePlus img, int param) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(param);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
    
    
    /**
     * Find background image intensity
     * with/without roi
     * read stats median intensity
     * @param img
     * @param roi
     * @param method
     * @return 
     */
    public double findBackground(ImagePlus img, Roi roi) {
      ImageProcessor imp = img.getProcessor();
      if (roi != null) {
          roi.setLocation(0, 0);
          imp.setRoi(roi);
      }
      double bg = imp.getStatistics().median;
      return(bg);
    }
    
    
    /**
     * Auto find background from scroolling roi
     * @param img
     * @return 
     */
    public double findRoiBackgroundAuto(ImagePlus img) {
        // scroll image and measure bg intensity in roi 
        // take roi lower intensity
        ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
        ArrayList<Double> intBgFound = new ArrayList<>();
        for (int x = 0; x < img.getWidth() - roiBgSize; x += roiBgSize) {
            for (int y = 0; y < img.getHeight() - roiBgSize; y += roiBgSize) {
                Roi roi = new Roi(x, y, roiBgSize, roiBgSize);
                double bg = findBackground(imgProj, roi);
                intBgFound.add(bg);
            }
        }
        flush_close(imgProj);
        img.deleteRoi();
        // get min value
        double bg = Collections.min(intBgFound);
        System.out.println("Background = " + bg);
        return(bg);
    }
    
    /**
     * Write results
     */
    public void writeResults(ArrayList<Cell> cells, ImagePlus imgGal, ImagePlus imgMafa, String fileName, BufferedWriter results) throws IOException {
        int cellIndex = 0;
        // find background
        double galBg = findRoiBackgroundAuto(imgGal);
        double mafaBg = findRoiBackgroundAuto(imgMafa);
        for (Cell cell : cells) {
            cellIndex++;
            Object3DInt galCell = cell.getGalCell();
            float galLabel = 0;
            double galVol = 0;
            double galIntGal = 0;
            double galIntMafa = 0;
            Object3DInt mafaCell = cell.getMafaCell();
            float mafaLabel = 0;
            double mafaVol = 0;
            double mafaIntGal = 0;
            double mafaIntMafa = 0;
            if (galCell != null) {
                galLabel = galCell.getLabel();
                galVol = new MeasureVolume(galCell).getVolumeUnit();
                galIntGal = new MeasureIntensity(galCell, ImageHandler.wrap(imgGal)).getValueMeasurement(MeasureIntensity.INTENSITY_AVG);
                galIntMafa = new MeasureIntensity(galCell, ImageHandler.wrap(imgMafa)).getValueMeasurement(MeasureIntensity.INTENSITY_AVG);
            }
            if (mafaCell != null) {
                mafaLabel = mafaCell.getLabel();
                mafaVol = new MeasureVolume(mafaCell).getVolumeUnit();
                mafaIntGal = new MeasureIntensity(mafaCell, ImageHandler.wrap(imgGal)).getValueMeasurement(MeasureIntensity.INTENSITY_AVG);
                mafaIntMafa = new MeasureIntensity(mafaCell, ImageHandler.wrap(imgMafa)).getValueMeasurement(MeasureIntensity.INTENSITY_AVG);        
            }
            results.write(fileName+"\t"+cellIndex+"\t"+galLabel+"\t"+galVol+"\t"+galIntGal+"\t"+(galIntGal- galBg)+"\t"+galIntMafa+"\t"+(galIntMafa - mafaBg)+
                            "\t"+mafaLabel+"\t"+mafaVol+"\t"+mafaIntGal+"\t"+(mafaIntGal - galBg)+"\t"+mafaIntMafa+"\t"+(mafaIntMafa - mafaBg)+"\n");
            results.flush();
        }
    }
} 
