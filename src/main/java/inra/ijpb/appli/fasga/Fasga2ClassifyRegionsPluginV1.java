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
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.data.image.ColorImages;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.strel.SquareStrel;
import inra.ijpb.segment.Threshold;
import inra.ijpb.util.ColorMaps;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.image.ColorModel;
import java.io.File;
import java.util.HashMap;

/**
 * @author David Legland
 *
 */
public class Fasga2ClassifyRegionsPluginV1 implements ExtendedPlugInFilter, DialogListener 
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
	int blueThreshold = 160;
	int redThreshold = 180;
	
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
			String newName = imagePlus.getShortTitle() + "-classified";
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

    	GenericDialog gd = new GenericDialog("Fasga Classify Regions");
		gd.addNumericField("Dark Regions Threshold", blueThreshold, 0);
		gd.addNumericField("Red Regions Threshold", redThreshold, 0);
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
	public void run(ImageProcessor image)
	{ 
		IJ.log("run fasga classify regions");
		
		// Execute core of the plugin
		this.result = labelsToRgb(computeClassifiedImage(image));

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
		this.blueThreshold = (int) gd.getNextNumber();
		this.redThreshold = (int) gd.getNextNumber();
    }

    @Override
	public void setNPasses(int nPasses)
	{
    	this.nPasses = nPasses;
	}

	private ImageProcessor computeClassifiedImage(ImageProcessor image)
	{
		// apply morphological filtering for removing cell wall images
		IJ.log("Start classify regions");
		// split channels
		IJ.log("  split channels");
		
		HashMap<String, ByteProcessor> channels = ColorImages.mapChannels(image);
		ByteProcessor red = channels.get("red");
		ByteProcessor blue = channels.get("blue");
		
		// un peu de morpho math pour lisser
		IJ.log("  segment stem");
		// Extract bundles + sclerenchyme
		ImageProcessor darkRegions = Threshold.threshold(blue, 0, blueThreshold);

		// Add morphological processing to keep stem image
		IJ.log("  fill holes");
		ImageProcessor stem = GeodesicReconstruction.fillHoles(darkRegions);
		stem = BinaryImages.keepLargestRegion(stem);
		
		stem.invertLut();

		IJ.log("  Compute Red Region");
		
		// Extract red area
		ImageProcessor redZone = Threshold.threshold(red, redThreshold, 255);
		
		// combine with stem image to remove background
		constrainToMask(redZone, stem);
		
		// combine with stem image to remove background
		constrainToMask(darkRegions, stem);
		
		// Compute rind image
		IJ.log("  Compute Rind");
		Strel seOp = SquareStrel.fromRadius(15); 
		Strel seCl = SquareStrel.fromRadius(4);
		ImageProcessor darkRegions2 = seCl.closing(seOp.opening(darkRegions));
		ImageProcessor rind = BinaryImages.keepLargestRegion(darkRegions2);		
		
		// Compute bundles image
		IJ.log("  Compute Bundles");
		ImageProcessor bundles = BinaryImages.removeLargestRegion(darkRegions2);
		bundles = BinaryImages.areaOpening(bundles, 200);
		
		// computes Blue region, as the union of non red and non dark
		IJ.log("  Compute Blue Region");
		ImageCalculator.Operation op = ImageCalculator.Operation.AND; 
		ImageProcessor blueZone = ImageCalculator.combineImages(not(redZone), not(rind), op);
		blueZone = ImageCalculator.combineImages(blueZone, not(bundles), op);
		constrainToMask(blueZone, stem);

		IJ.log("  Compute Labels");
		ImageProcessor labelImage = createLabelImage(redZone, blueZone, rind, bundles);
		labelImage.setMinAndMax(0, 4);
		
		return labelImage;
		
	}
	
	private static final ColorProcessor labelsToRgb(ImageProcessor labelImage)
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

//	public final static ColorModel createLabelsColorModel() {
//		// initial values
//		int[] r = new int[255];
//		int[] g = new int[255];
//		int[] b = new int[255];
//		r[0] = 255; g[0] = 255; b[0] = 255;
//		r[1] = 255; g[1] = 127; b[1] = 255;
//		r[2] =   0; g[2] =   0; b[2] = 255;
//		r[3] =   0; g[3] =   0; b[3] =   0;
//		r[4] = 127; g[4] = 127; b[4] = 127;
//
////		int[] r = {255, 255, 0, 0, 127};
////		int[] g = {255, 127, 0, 0, 127};
////		int[] b = {255, 255, 255, 0, 127};
//
//		// create map
//		byte[][] map = new byte[r.length][3];
//		
//		// cast elements
//		for (int i = 0; i < r.length; i++) {
//			map[i][0] = (byte) r[i];
//			map[i][1] = (byte) g[i];
//			map[i][2] = (byte) b[i];
//		}
//		
//		return  ColorMaps.createColorModel(map);
//	}

	/**
	 * Computes the binary image that correspond to the stem. A dark object
	 * appearing on a light background is expected.
	 * 
	 * @param image
	 *            input image (converted to byte if needed)
	 * @return binary image processor corresponding to the stem
	 */
	public static ImageProcessor segmentStem(ImageProcessor image)
	{
		// convert to byte if needed
		ImageProcessor res;
		if (image instanceof ByteProcessor)
		{
			res = (ByteProcessor) image.duplicate();
		} else
		{
			res = (ByteProcessor) image.convertToByte(false);
		}

		// compute some statistics, including histogram
		ImageStatistics stats = res.getStatistics();

		// compute threshold using Otsu auto-threshold method
		AutoThresholder thresholder = new AutoThresholder();
		AutoThresholder.Method method = AutoThresholder.Method.Otsu;
		int threshold = thresholder.getThreshold(method, stats.histogram);

		// Apply threshold to gray-scale image
		res = inra.ijpb.segment.Threshold.threshold(res, 0, threshold);

		// fill holes in image
		GeodesicReconstruction.fillHoles(res);

		return res;
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
	
	private static final int getColorCode(int r, int g, int b)
	{
		return (r & 0x00FF) << 16 | (g & 0x00FF) << 8 | (b & 0x00FF); 
	}
	
	public static final void main(String[] args) 
	{
		System.out.println("run main");
		
		new ImageJ();
		
		System.out.println(new File(".").getAbsolutePath());
		File file = new File("./src/test/resources/files/maize-crop.tif");
		
		ImagePlus imagePlus = IJ.openImage(file.getPath());
		if (imagePlus == null) 
		{
			throw new RuntimeException("Could not read input image");
		}
		imagePlus.show();
		
		Fasga2ClassifyRegionsPluginV1 plugin = new Fasga2ClassifyRegionsPluginV1();
		plugin.setup("run", imagePlus);
		plugin.showDialog(imagePlus, "Truc", null);
		plugin.run(imagePlus.getProcessor());
		
		new ImagePlus("Result", plugin.result).show();
	}
}
