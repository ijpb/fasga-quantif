/**
 * 
 */
package inra.ijpb.appli.fasga;

import static inra.ijpb.math.ImageCalculator.not;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
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
import java.io.File;

/**
 * Classify an image of maize stem after fasga coloration to identify various tissues type.
 *    
 * @author David Legland
 *
 */
public class Fasga2SegmentRegionsPlugin implements ExtendedPlugInFilter, DialogListener 
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
	
	/** The image representing the filtered stem */
	private ImageProcessor filteredImage;

	/** The binary image of the segmented stem */
	private ImageProcessor stemImage;
	
	/** Keep instance of result label image */
	private ImageProcessor result;
	private ImageProcessor resultRGB;

	// parameters of the plugin
	int darkRegionThreshold = 130;
	int redRegionThreshold = 170;
	int bundlesMinPixelNumber = 100;
	int bundlesMaxPixelNumber = 6000;

	
	// A list of preview images, stored in plugin to avoid creating many many images...
	static ImagePlus darkRegionsImagePlus = null;
	static ImagePlus bundlesImagePlus = null;
	static ImagePlus redRegionImagePlus = null;
	static ImagePlus blueRegionImagePlus = null;
	
	// Table of RGB values associated to each region
	int[][] labelColors = new int[][] { 
			new int[]{255, 255, 255},	// Background is white
			new int[]{255,   0, 255}, 	// red region
			new int[]{  0, 127, 255},	// blue region (dark cyan)
			new int[]{  0,   0,   0},	// rind is black
			new int[]{255, 255,   0} };	// bundles are yellow

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
		
		int[] indices = WindowManager.getIDList();
		if (indices == null)
		{
			IJ.error("No image", "Need at least one image to work");
			return DONE;
		}
		String[] imageNames = new String[indices.length];
		for (int i = 0; i < indices.length; i++) 
		{
			imageNames[i] = WindowManager.getImage(indices[i]).getTitle();
		}
		
		// Normal setup
    	this.imagePlus = imp;
    	this.baseImage = imp.getProcessor().duplicate();

    	GenericDialog gd = new GenericDialog("Fasga Segment Regions");
    	gd.addChoice("Filtered Image", imageNames, IJ.getImage().getTitle());
    	gd.addChoice("Stem Image", imageNames, IJ.getImage().getTitle());
		gd.addNumericField("Dark Regions Threshold", darkRegionThreshold, 0);
		gd.addNumericField("Red Regions Threshold", redRegionThreshold, 0);
		gd.addNumericField("Bundles_Min. Size (pixels)", 100, 0);
		gd.addNumericField("Bundles_Max. Size (pixels)", 6000, 0);

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

    private void parseDialogParameters(GenericDialog gd)
    {
		// Extract filtered image
    	int filteredImageIndex = gd.getNextChoiceIndex();
    	ImagePlus filteredPlus = WindowManager.getImage(filteredImageIndex + 1);
		this.filteredImage = filteredPlus.getProcessor();

		// Extract stem image
    	int stemImageIndex = gd.getNextChoiceIndex();
		ImagePlus stemPlus = WindowManager.getImage(stemImageIndex + 1);
		this.stemImage = stemPlus.getProcessor();
		
		// extract processing parameters
		this.darkRegionThreshold = (int) gd.getNextNumber();
		this.redRegionThreshold = (int) gd.getNextNumber();
		this.bundlesMinPixelNumber = (int) gd.getNextNumber();
		this.bundlesMaxPixelNumber = (int) gd.getNextNumber();
    }

    @Override
	public void setNPasses(int nPasses)
	{
    	this.nPasses = nPasses;
	}

	@Override
	public void run(ImageProcessor image)
	{ 
		// check validity
		if (this.filteredImage == null)
			return;
		if (this.stemImage == null)
			return;
		
		// Execute core of the plugin
		this.result = segmentStemRegions(this.filteredImage, this.stemImage, 
				this.darkRegionThreshold, this.redRegionThreshold, 
				this.bundlesMinPixelNumber, this.bundlesMaxPixelNumber, true);
		this.resultRGB = ColorUtils.colorizeLabelImage(this.result, this.labelColors);
				
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
	public static final ImageProcessor segmentStemRegions(ImageProcessor image,
			ImageProcessor stemImage, int darkRegionsThreshold,
			int redRegionThreshold, int minBundleSizeInPixels, int maxBundleSizeInPixels,
			boolean showImages)	
	{
		// apply morphological filtering for removing cell wall images
		IJ.log("Start regions segmentation");
		
		// check image type
		if (!(image instanceof ColorProcessor)) 
		{
			throw new IllegalArgumentException("Requires a color image as first input");
		}
		
		// First extract hue and brightness as float processors (between 0 and 1)
		IJ.log("  Extract color components");
		ColorProcessor colorImage = (ColorProcessor) image;
		FloatProcessor hue = ColorUtils.computeHue(colorImage);
		FloatProcessor brightness = colorImage.getBrightness();

		// identify dark regions (-> either rind or bundles)
		IJ.log("  Extract dark regions");
		// Extract bundles + sclerenchyme
		ImageProcessor darkRegions = Threshold.threshold(brightness, 0, darkRegionsThreshold / 255.0);
		constrainToMask(darkRegions, stemImage);
		if (showImages)
		{
			darkRegionsImagePlus = updatePreview(darkRegionsImagePlus, darkRegions, "Dark Regions");
		}
		
		// Compute rind image, as the largest dark region
		IJ.log("  Compute Rind");
//		ImageProcessor rind = BinaryImages.keepLargestRegion(darkRegions);
		ImageProcessor rind = BinaryImages.areaOpening(darkRegions, maxBundleSizeInPixels);
		
		stemImage = ImageCalculator.combineImages(stemImage, rind, ImageCalculator.Operation.OR);
		
		
		// Compute bundles image, by removing rind and filtering remaining image
		IJ.log("  Compute Bundles");
//		ImageProcessor bundles = BinaryImages.removeLargestRegion(darkRegions);
		ImageProcessor bundles = ImageCalculator.combineImages(darkRegions, rind, ImageCalculator.Operation.XOR);
		
		bundles = GeodesicReconstruction.fillHoles(bundles);
		bundles = BinaryImages.areaOpening(bundles, minBundleSizeInPixels);
		if (showImages) 
		{
			bundlesImagePlus = updatePreview(bundlesImagePlus, bundles, "Bundles");
		}
		
		// Extract red area
		ImageProcessor redZone = Threshold.threshold(hue, redRegionThreshold / 255.0, 1);
		
		// combine with stem image to remove background
		constrainToMask(redZone, stemImage);
		if (showImages) 
		{
			redRegionImagePlus = updatePreview(redRegionImagePlus, redZone, "Red Region");
		}
		
		// combine with stem image to remove background
		constrainToMask(darkRegions, stemImage);

		// computes Blue region, as the union of non red and non dark
		IJ.log("  Compute Blue Region");
		ImageCalculator.Operation op = ImageCalculator.Operation.AND; 
		ImageProcessor blueZone = ImageCalculator.combineImages(not(redZone), not(rind), op);
		blueZone = ImageCalculator.combineImages(blueZone, not(bundles), op);
		constrainToMask(blueZone, stemImage);
		if (showImages) 
		{
			blueRegionImagePlus = updatePreview(blueRegionImagePlus, blueZone, "Blue Region");
		}

		IJ.log("  Compute Labels");
		ImageProcessor labelImage = createLabelImage(redZone, blueZone, rind, bundles);
		labelImage.setMinAndMax(0, 4);
		IJ.log("  (end of region segmentation)");

		return labelImage;
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

		if (mask.getWidth() != width || mask.getHeight() != height)
		{
			IJ.error("Both images must have the same size");
			throw new IllegalArgumentException("Input images must have the same size");
		}
		
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
		
		// Compute stem image
		ImageProcessor image = imagePlus.getProcessor();
		ImageProcessor stemImage = Fasga2SegmentStemPlugin.segmentStem(image, .99, .97, 10, false);
		ImagePlus stemPlus = new ImagePlus("Stem", stemImage);
		stemPlus.show();
		
		Fasga2SegmentRegionsPlugin plugin = new Fasga2SegmentRegionsPlugin();
		plugin.setup("run", imagePlus);
		plugin.showDialog(imagePlus, "Truc", null);
		plugin.run(imagePlus.getProcessor());
		
		new ImagePlus("Regions", plugin.result).show();
	}
}
