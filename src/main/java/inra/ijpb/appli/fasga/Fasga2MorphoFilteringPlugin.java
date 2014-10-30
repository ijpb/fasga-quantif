/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import inra.ijpb.event.DefaultAlgoListener;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.strel.OctagonStrel;

import java.awt.AWTEvent;
import java.io.File;

/**
 * @author David Legland
 *
 */
public class Fasga2MorphoFilteringPlugin implements ExtendedPlugInFilter, DialogListener 
{

	/** Apparently, it's better to store flags in plugin */
	private int flags = DOES_ALL | KEEP_PREVIEW | FINAL_PROCESSING;
	
	PlugInFilterRunner pfr;
	int nPasses;
	boolean previewing = false;
	
	/** need to keep the instance of ImagePlus */ 
	private ImagePlus imagePlus;
	
	/** keep the original image, to restore it after the preview */
	private ImageProcessor baseImage;
	
	/** Keep instance of result image */
	private ImageProcessor result;

	// parameters of the plugin
	int closingRadius;
	int openingRadius;
	double sigma;
	
	
	/**
	*/
	public int setup(String arg, ImagePlus imp) {

		// Called at the end of plugin for validating result
		// -> opens a new frame with result image, and cleanup original frame
		if (arg.equals("final")) {
			IJ.log("  setup(\"final\")");
			
			// replace the preview image by the original image 
			imagePlus.setProcessor(baseImage);
			imagePlus.updateAndDraw();
			
			// Create a new ImagePlus with the filter result
			String newName = imagePlus.getShortTitle() + "-filtered";
			ImagePlus resPlus = new ImagePlus(newName, result);
			resPlus.copyScale(imagePlus);
			resPlus.show();
			return DONE;
		}
		
		return flags;
	}

	@Override
	public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
	{
		System.out.println("show dialog");
		
		// Normal setup
    	this.imagePlus = imp;
    	this.baseImage = imp.getProcessor().duplicate();

    	GenericDialog gd = new GenericDialog("Fasga Filtering 2");
		gd.addNumericField("Cell Wall Size", 6, 0);
		gd.addNumericField("Bright Areas Size", 12, 0);
		gd.addNumericField("Gaussian Smoothing", 4, 1);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		previewing = true;
		gd.showDialog();
		previewing = false;

		if (gd.wasCanceled())
			return DONE;

		parseDialogParameters(gd);

		// clean up an return 
		gd.dispose();
		return flags;
	}

	
	@Override
	public void run(ImageProcessor image)
	{ 
		IJ.log("run fasga morphological filtering");
		
		// Execute core of the plugin
		this.result = computeFilteredImage(image, this.closingRadius, this.openingRadius, this.sigma);

    	if (previewing) {
    		// Fill up the values of original image with values of the result
    		for (int i = 0; i < image.getPixelCount(); i++) {
    			image.set(i, result.get(i));
    		}
        }
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent evt)
	{
		System.out.println("dialog item changed");
	
		parseDialogParameters(gd);
    	return true;
	}

    private void parseDialogParameters(GenericDialog gd) {
		// Extract parameters
		this.closingRadius = (int) gd.getNextNumber();
		this.openingRadius = (int) gd.getNextNumber();
		this.sigma = gd.getNextNumber();
    }

    @Override
	public void setNPasses(int nPasses)
	{
    	this.nPasses = nPasses;
	}

	public final static ImageProcessor computeFilteredImage(
			ImageProcessor image, int closingRadius, int openingRadius,
			double sigma)
	{
		// apply morphological filtering for removing cell wall images
		IJ.log("Start color filtering");
		IJ.log("   closing");
		Strel closingStrel = OctagonStrel.fromRadius(closingRadius);
		DefaultAlgoListener.monitor(closingStrel);
		
		ImageProcessor filtered = Morphology.closing(image, closingStrel);

		IJ.log("   opening");
		Strel openingStrel = OctagonStrel.fromRadius(openingRadius);
		DefaultAlgoListener.monitor(openingStrel);
		filtered = Morphology.opening(filtered, openingStrel);

		// apply gaussian blur radius 4
		IJ.log("   smooth");
		// IJ.runPlugIn("Gaussian Blur...", "sigma=4");
		new GaussianBlur().blurGaussian(filtered, sigma, sigma, .01);

		IJ.log("   done.");
		return filtered;
	}


	public static final void main(String[] args) 
	{
		System.out.println("run main");
		
		new ImageJ();
		
		System.out.println(new File(".").getAbsolutePath());
		File file = new File("./src/main/resources/files/maize-crop.tif");
		
		ImagePlus imagePlus = IJ.openImage(file.getPath());
		if (imagePlus == null) 
		{
			throw new RuntimeException("Could not read input image");
		}
		imagePlus.show();
		
		Fasga2MorphoFilteringPlugin plugin = new Fasga2MorphoFilteringPlugin();
		plugin.setup("run", imagePlus);
		plugin.showDialog(imagePlus, "Truc", null);
		plugin.run(imagePlus.getProcessor());
		
		new ImagePlus("Result", plugin.result).show();
	}
}
