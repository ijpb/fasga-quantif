/**
 * 
 */
package inra.ijpb.appli.fasga;

import static inra.ijpb.math.ImageCalculator.not;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.Reconstruction;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.strel.DiskStrel;
import inra.ijpb.segment.Threshold;

import java.awt.AWTEvent;
import java.io.File;

/**
 * Identify the stem in a color image of fasga-stained maize stem section after
 * morphological filtering. 
 *    
 * Aims at managing following artifacts:
 * <ul>
 * <li>Holes in the stem (using hysteresis threshold)</li>
 * <li>Bubbles around the slice (using binary morphological filtering)</li>
 * </ul>
 * @author David Legland
 *
 */
public class Fasga2SegmentStemPlugin implements ExtendedPlugInFilter, DialogListener 
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
	
	/** Keep instance of result label image */
	private ImageProcessor result;

	// parameters of the plugin
	double highThresholdHoles = .999;
	double lowThresholdHoles = .99;
	int bubblesDiameterPx = 20;
	
	
	// A list of preview images, stored in plugin to avoid creating many many images...
	static ImagePlus stemImagePlus = null;
	static ImagePlus darkRegionsImagePlus = null;
	static ImagePlus bundlesImagePlus = null;
	static ImagePlus redRegionImagePlus = null;
	static ImagePlus blueRegionImagePlus = null;
	
	/**
	*/
	public int setup(String arg, ImagePlus imp) 
	{
		// Called at the end of plugin for validating result
		// -> opens a new frame with result image, and cleanup original frame
		if (arg.equals("final")) 
		{
			IJ.log("  setup(\"final\")");
			
			// replace the preview image by the original image 
			imagePlus.setProcessor(baseImage);
			imagePlus.updateAndDraw();
			
			// Create a new ImagePlus with the labels
			String newName = imagePlus.getShortTitle() + "-stem";
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

    	GenericDialog gd = new GenericDialog("Fasga Segment Stem");
    	gd.addNumericField("High threshold for holes (0->1)", highThresholdHoles, 4);
    	gd.addNumericField("Low threshold for holes (0->1)", lowThresholdHoles, 4);
		gd.addNumericField("Bubbles Thickness (pixels)", bubblesDiameterPx, 0);

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
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent evt)
	{
		parseDialogParameters(gd);
    	return true;
	}

    private void parseDialogParameters(GenericDialog gd) {
		// Extract parameters
		this.highThresholdHoles = gd.getNextNumber();
		this.lowThresholdHoles 	= gd.getNextNumber();
		this.bubblesDiameterPx 	= (int) gd.getNextNumber();
    }

    @Override
	public void setNPasses(int nPasses)
	{
    	this.nPasses = nPasses;
	}

	@Override
	public void run(ImageProcessor image)
	{ 
		// Execute core of the plugin
		this.result = segmentStem(image,
				this.highThresholdHoles, this.lowThresholdHoles, 
				this.bubblesDiameterPx, true);
				
    	if (previewing) 
    	{
    		// Fill up the values of original image with values of the result
    		for (int i = 0; i < image.getPixelCount(); i++) 
    		{
    			image.set(i, result.get(i) * 0x010101);
    		}
        }
	}

	/**
	 * Computes a label image corresponding to the stem.
	 */
	public static final ImageProcessor segmentStem(ImageProcessor image,
			double holeThresholdHigh, double holeThresholdLow, 
			int bubblesDiameterPx, boolean showImages)
	{
		// apply morphological filtering for removing cell wall images
		IJ.log("Start segmenting stem");
		
		// check image type
		if (!(image instanceof ColorProcessor)) 
		{
			throw new IllegalArgumentException("Requires a color image as first input");
		}
		
		// First extract luma as float processors (between 0 and 1)
		IJ.log("  Extract Luma component");
		ColorProcessor colorImage = (ColorProcessor) image;
		FloatProcessor luma = ColorUtils.computeLuma(colorImage);

		// Segment stem using threshold on luminance
		IJ.log("  Binarize Image");
		ImageProcessor stem = Threshold.threshold(luma, 0, 200.0 / 255.0);
		stem = Reconstruction.fillHoles(stem);
		
		// Morphological filtering to remove boundary of bubbles
		IJ.log("  Remove bubbles");
//		Strel se = DiskStrel.fromDiameter(bubblesDiameterPx);
		Strel se = DiskStrel.fromRadius((bubblesDiameterPx - 1)/ 2);
		stem = Morphology.opening(stem, se);

		// remove small components
		stem = BinaryImages.keepLargestRegion(stem);

		// detect eventual holes in the stem
		IJ.log("  Detect holes");
		ImageProcessor holes = Threshold.threshold(luma, holeThresholdHigh, 1.0);
		ImageProcessor holes2 = Threshold.threshold(luma, holeThresholdLow, 1.0);
		holes = Reconstruction.reconstructByDilation(holes, holes2);
		
		// combine image of stem with image of holes
		stem = ImageCalculator.combineImages(stem, not(holes), ImageCalculator.Operation.AND);
		if (showImages)
		{
			stemImagePlus = updatePreview(stemImagePlus, stem, "Segmented Stem");
		}
	

		return stem;
	}
	
	private static ImagePlus updatePreview(ImagePlus imagePlus, ImageProcessor image, String title)
	{
		if (imagePlus == null)
		{
			imagePlus = new ImagePlus(title, image);	
			imagePlus.show();
		}
		else
		{
			imagePlus.setProcessor(image);
			imagePlus.repaintWindow();
		}
		return imagePlus;
	}
	
	
	public static final void main(String[] args) 
	{
		System.out.println("run main");
		
		new ImageJ();
		
		System.out.println(new File(".").getAbsolutePath());
//		File file = new File("./src/test/resources/files/maize-crop-filtered.tif");
		File file = new File("./src/test/resources/files/F98902S4-crop-scale20-filtered.tif");
		
		ImagePlus imagePlus = IJ.openImage(file.getPath());
		if (imagePlus == null) 
		{
			throw new RuntimeException("Could not read input image");
		}
		imagePlus.show();
		
		Fasga2SegmentStemPlugin plugin = new Fasga2SegmentStemPlugin();
		plugin.setup("run", imagePlus);
		plugin.showDialog(imagePlus, "Truc", null);
		plugin.run(imagePlus.getProcessor());
		
//		new ImagePlus("Result", plugin.result).show();
	}
}
