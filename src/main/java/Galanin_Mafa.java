import Galanin_Mafa_Tools.Tools;
import Galanin_Mafa_Tools.Cell;
import ij.*;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.in.ImporterOptions;
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;


/**
 * Detect DAPI nuclei, Astro cells and Ocytocin foci
 * Take only Astro with nucleus (coloc)
 * Detect Ocytocin foci in Astro cells
 * @author Orion-CIRB
 */

public class Galanin_Mafa implements PlugIn {
    
    Tools tools = new Tools();
    
    private String imageDir = "";
    public String outDirResults = "";
    public BufferedWriter results;
   
    
    public void run(String arg) {
        try {
            if ((!tools.checkInstalledModules())) {
                return;
            } 
            
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }   
            System.out.println(imageDir);
            // Find images with extension
            String fileExt = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles.size() == 0) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.cal = tools.findImageCalib(meta);
            
            // Find channel names
            String[] chsName = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Channels dialog
            String[] channels = tools.dialog(chsName);
            if (channels == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }
            
            // Create output folder
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            Date date = new Date();
            outDirResults = imageDir + File.separator+ "Results_" + dateFormat.format(date) +  File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write header in results file
            String header = "Image name\t#cell\tGalanin cell label\tGalanin cell volume (µm3)\tGalanin cell intensity in gal ch\t"
                    + "Galanin cell bg corr. intensity in gal ch\tGalanin cell intensity in Mafa ch\tGalanin cell ng corr. intensity in Mafa ch\t"
                    + "Mafa cell label\tMafa cell volume (µm3)\tMafa cell intensity in gal ch\tMafa cell intensity in mafa ch\n";
            FileWriter fwResults = new FileWriter(outDirResults + "results.xls", false);
            results = new BufferedWriter(fwResults);
            results.write(header);
            results.flush();
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Open galanin channel
                tools.print("- Analyzing Galanin channel -");
                int indexCh = ArrayUtils.indexOf(chsName, channels[0]);
                ImagePlus imgGal = BF.openImagePlus(options)[indexCh];
                // Detect galanin cells with CellPose
                System.out.println("Finding Galanin cells ...");
                Objects3DIntPopulation galPop = tools.cellposeDetection(imgGal);
                int galNb = galPop.getNbObjects();
                System.out.println(galNb + " Galanin cells found");
                
                
                // Open Mafa channel
                tools.print("- Analyzing Mafa channel -");
                indexCh = ArrayUtils.indexOf(chsName, channels[1]);
                ImagePlus imgMafa = BF.openImagePlus(options)[indexCh];
                // Detect Mafa cells with CellPose
                System.out.println("Finding Mafa cells....");
                Objects3DIntPopulation mafaPop = tools.cellposeDetection(imgMafa);
                int mafaNb = mafaPop.getNbObjects();
                System.out.println(mafaNb + " Mafa cells found");
                
                // Find galanin / mafa coloc
                System.out.println("Finding colocalization galanin with mafa cells....");
                ArrayList<Cell> cells = new ArrayList();
                cells = tools.findColocPop(cells, galPop, mafaPop, "gal");
                // Find mafa / galanin coloc
                cells = tools.findColocPop(cells, mafaPop, galPop, "mafa");
                
                // Save image objects
                tools.print("- Saving results -");
                tools.drawResults(galPop, mafaPop, imgGal, imgMafa, rootName, outDirResults);
                
                // Write results
                tools.writeResults(cells, imgGal, imgMafa, rootName, results);
                
                tools.flush_close(imgGal);
                tools.flush_close(imgMafa);
            }
            results.close();
        } catch (IOException | DependencyException | ServiceException | FormatException | io.scif.DependencyException  ex) {
            Logger.getLogger(Galanin_Mafa.class.getName()).log(Level.SEVERE, null, ex);
        }
        tools.print("--- Process done ---");
    }    
}    
