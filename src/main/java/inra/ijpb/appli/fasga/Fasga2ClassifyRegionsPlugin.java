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
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.segment.Threshold;

import java.awt.AWTEvent;
import java.awt.Color;
import java.io.File;

/**
 * Classify an image of maize stem after fasga coloration to identify various tissues type.
 *    
 * @author David Legland
 *
 */
public class Fasga2ClassifyRegionsPlugin implements ExtendedPlugInFilter, DialogListener 
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
	private ImageProcessor resultRGB;

	// parameters of the plugin
	int darkRegionThreshold = 130;
	int redRegionThreshold = 170;
	
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
			String newName = imagePlus.getShortTitle() + "-regions";
			ImagePlus resPlus = new ImagePlus(newName, result);
			resPlus.copyScale(imagePlus);
			resPlus.show();

			// Create a new ImagePlus with the colorized labels
			newName = imagePlus.getShortTitle() + "-regionsRGB";
			resPlus = new ImagePlus(newName, resultRGB);
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

    	GenericDialog gd = new GenericDialog("Fasga Classify Regions");
		gd.addNumericField("Dark Regions Threshold", darkRegionThreshold, 0);
		gd.addNumericField("Red Regions Threshold", redRegionThreshold, 0);
//		gd.addNumericField("Gaussian Smoothing", 4, 1);

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
		this.darkRegionThreshold = (int) gd.getNextNumber();
		this.redRegionThreshold = (int) gd.getNextNumber();
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
		this.result = computeStemRegions(image,
				this.darkRegionThreshold, this.redRegionThreshold, true);
		this.resultRGB = colorizeRegionImage(this.result);
				
    	if (previewing) 
    	{
    		// Fill up the values of original image with values of the result
    		for (int i = 0; i < image.getPixelCount(); i++) 
    		{
    			image.set(i, resultRGB.get(i));
    		}
        }
	}

	/**
	 * Computes a label image corresponding to different regions in the stem.
	 */
	public static final ImageProcessor computeStemRegions(ImageProcessor image,
			int darkRegionsThreshold, int redRegionThreshold, boolean showImages)
	{
		// apply morphological filtering for removing cell wall images
		IJ.log("Start classify regions");
		
		// check image type
		if (!(image instanceof ColorProcessor)) 
		{
			throw new IllegalArgumentException("Requires a color image as first input");
		}
		
		// First extract hue and brightness as float processors (between 0 and 1)
		IJ.log("  Extract Hue and Brightness components");
		ColorProcessor colorImage = (ColorProcessor) image;
		FloatProcessor hue = computeHue(colorImage);
		FloatProcessor luma = computeLuma(colorImage);
		FloatProcessor brightness = colorImage.getBrightness();

//		if (showImages)
//		{
//			new ImagePlus("Hue", hue).show();
//			new ImagePlus("Brightness", brightness).show();
//		}
		

		// Stem image with different threshold, but keep rind within.
		IJ.log("  Compute Stem Image");
		ImageProcessor stem = Threshold.threshold(brightness, 0, 200.0 / 255.0);
		stem = GeodesicReconstruction.fillHoles(stem);
		stem = BinaryImages.keepLargestRegion(stem);
		
		// detect eventual holes in the stem
		IJ.log("  Detect holes");
		ImageProcessor holes = Threshold.threshold(luma, .99, 1.0);
		ImageProcessor holes2 = Threshold.threshold(luma, .97, 1.0);
		holes = GeodesicReconstruction.reconstructByDilation(holes, holes2);
		
		// combine image of stem with image of holes
		stem = ImageCalculator.combineImages(stem, not(holes), ImageCalculator.Operation.AND);
		if (showImages)
		{
			new ImagePlus("Segmented Stem", stem).show();
		}
	

		// identify dark regions (-> either rind or bundles)
		IJ.log("  Extract dark regions");
		// Extract bundles + sclerenchyme
		ImageProcessor darkRegions = Threshold.threshold(brightness, 0, darkRegionsThreshold / 255.0);
		if (showImages)
		{
			new ImagePlus("Dark Regions", darkRegions).show();
		}
		
		// Compute rind image, as the largest dark region
		IJ.log("  Compute Rind");
		ImageProcessor rind = BinaryImages.keepLargestRegion(darkRegions);
		stem = ImageCalculator.combineImages(stem, rind, ImageCalculator.Operation.OR);
		
		
		// Compute bundles image, by removing rind and filtering remaining image
		IJ.log("  Compute Bundles");
		ImageProcessor bundles = BinaryImages.removeLargestRegion(darkRegions);
		bundles = GeodesicReconstruction.fillHoles(bundles);
		bundles = BinaryImages.areaOpening(bundles, 250);
		if (showImages) 
		{
			new ImagePlus("Bundles", bundles).show();
		}
		
		// Extract red area
		ImageProcessor redZone = Threshold.threshold(hue, redRegionThreshold / 255.0, 1);
		
		// combine with stem image to remove background
		constrainToMask(redZone, stem);
		if (showImages) 
		{
			new ImagePlus("Red Region", redZone).show();
		}
		
		// combine with stem image to remove background
		constrainToMask(darkRegions, stem);

		// computes Blue region, as the union of non red and non dark
		IJ.log("  Compute Blue Region");
		ImageCalculator.Operation op = ImageCalculator.Operation.AND; 
		ImageProcessor blueZone = ImageCalculator.combineImages(not(redZone), not(rind), op);
		blueZone = ImageCalculator.combineImages(blueZone, not(bundles), op);
		constrainToMask(blueZone, stem);
		if (showImages) 
		{
			new ImagePlus("Blue Region", blueZone).show();
		}

		IJ.log("  Compute Labels");
		ImageProcessor labelImage = createLabelImage(redZone, blueZone, rind, bundles);
		labelImage.setMinAndMax(0, 4);
		IJ.log("  (end of classification)");

		return labelImage;
	}
	
	private static final FloatProcessor computeHue(ColorProcessor image)
	{
		// get image size
		int width = image.getWidth();
		int height = image.getHeight();
		
		// allocate memory for result
		FloatProcessor result = new FloatProcessor(width, height);
		
		// iterate over pixels
		float[] hsb = new float[3]; 
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int c = image.get(x, y);
				int r = (c & 0xFF0000) >> 16;
				int g = (c & 0xFF00) >> 8;
				int b =  c & 0xFF;
				Color.RGBtoHSB(r, g, b, hsb);
				result.setf(x, y, hsb[0]);
			}
		}
		
		return result;
	}
	
	/**
	 * Compute luma component of a color image, as weighted sum of RGB
	 * components, and returns the result in a float processor instead of a
	 * ByteProcessor.
	 * 
	 */
	private static final FloatProcessor computeLuma(ColorProcessor image)
	{
		// get image size
		int width = image.getWidth();
		int height = image.getHeight();
		
		// allocate memory for result
		FloatProcessor result = new FloatProcessor(width, height);
		
		// iterate over pixels
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int c = image.get(x, y);
				int r = (c & 0xFF0000) >> 16;
				int g = (c & 0xFF00) >> 8;
				int b =  c & 0xFF;
				float luma = (r * .299f + g * .587f + b * .114f) / 255;
				result.setf(x, y, luma);
			}
		}
		
		return result;
	}

	
//	private static final ByteProcessor convertToByteProcessor(ImageProcessor image, boolean rescale) 
//	{
//		// get image size
//		int width = image.getWidth();
//		int height = image.getHeight();
//		
//		// Compute min and max value for rescaling
//		double minValue = Double.MAX_VALUE;
//		double maxValue = Double.MIN_VALUE;
//		if (rescale) 
//		{
//			double val;
//			for (int y = 0; y < height; y++)
//			{
//				for (int x = 0; x < width; x++)
//				{
//					val = image.getf(x, y);
//					minValue = Math.min(minValue, val);
//					maxValue = Math.max(maxValue, val);
//				}
//			}
//		}
//		else
//		{
//			minValue = 0;
//			maxValue = 255;
//		}
//
//		// Allocate new image
//		ByteProcessor result = new ByteProcessor(width, height);
//
//		// Rescale value of each pixel
//		for (int y = 0; y < height; y++)
//		{
//			for (int x = 0; x < width; x++)
//			{
//				double val = image.getf(x, y);
//				val = 255 * (val - minValue) / (maxValue - minValue);
//				result.set(x, y, (int) val);
//			}
//		}
//		
//		return result;
//	}
	
	public static final ColorProcessor colorizeRegionImage(ImageProcessor labelImage)
	{
		int width = labelImage.getWidth();
		int height = labelImage.getHeight();
		ColorProcessor colorImage = new ColorProcessor(width, height);
		
		int[] colorCodes = new int[] { 
				getColorCode(255, 255, 255),	// Background is white
				getColorCode(255,   0, 255), 	// red region
				getColorCode(  0, 127, 255),	// blue region (dark cyan)
				getColorCode(  0,   0,   0),	// rind is black
				getColorCode(127, 127, 127) };	// bundles are dark gray

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int label = labelImage.get(x, y);
				colorImage.set(x, y, colorCodes[label]);
			}
		} 
		
		return colorImage;
	}
	
	private static final int getColorCode(int r, int g, int b)
	{
		return (r & 0x00FF) << 16 | (g & 0x00FF) << 8 | (b & 0x00FF); 
	}
	

	/**
	 * Sets to zero all the pixels of input image that are not in the binary mask.
	 * 
	 * @param image a grayscale or RGB image
	 * @param mask a binary mask
	 */
	private static final void constrainToMask(ImageProcessor image, ImageProcessor mask) 
	{
		int width = image.getWidth();
		int height = image.getHeight();

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (mask.get(x, y) == 0)
					image.set(x, y, 0);
			}
		}
	}
	
	/**
	 * Create a new label image from a set of binary images. The label values 
	 * range between 1 and the number of images.
	 * 
	 * @param images a collection of binary images (0: background, >0 pixel belongs to current label)
	 * @return a new image with label values
	 */
	private static final ImageProcessor createLabelImage(ImageProcessor... images)
	{
		ImageProcessor refImage = images[0];
		int width = refImage.getWidth();
		int height = refImage.getHeight();
		
		ImageProcessor result = new ByteProcessor(width, height);
		
		int label = 0;
		for (ImageProcessor image : images) 
		{
			label++;
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					if (image.get(x, y) > 0) 
					{
						result.set(x, y, label);
					}
				}
			}
		}
		
		return result;
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
		
		Fasga2ClassifyRegionsPlugin plugin = new Fasga2ClassifyRegionsPlugin();
		plugin.setup("run", imagePlus);
		plugin.showDialog(imagePlus, "Truc", null);
		plugin.run(imagePlus.getProcessor());
		
		new ImagePlus("Result", plugin.result).show();
	}
}
