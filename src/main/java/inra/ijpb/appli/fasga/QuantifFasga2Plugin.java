/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.binary.ConnectedComponents;
import inra.ijpb.data.image.ColorImages;
import inra.ijpb.event.DefaultAlgoListener;
import inra.ijpb.label.LabelImages;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.measure.GeometricMeasures2D;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.strel.OctagonStrel;
import inra.ijpb.morphology.strel.SquareStrel;
import inra.ijpb.segment.Threshold;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import static inra.ijpb.math.ImageCalculator.not;

/**
 * Plugin integrating the various processing step of FAsga quantification. The steps include:
 * <ul>
 * <li> Morphological filtering</li> 
 * <li> Tissues classification</li> 
 * <li> Quantification (morphology and colorimetry)</li> 
 * </ul>
 * @author David Legland
 *
 */
public class QuantifFasga2Plugin implements PlugIn
{

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg0)
	{
		System.out.println("Start Plugin 'Quantif Fasga'");
		
		ImagePlus imagePlus = IJ.getImage();
		ImageProcessor image = imagePlus.getProcessor();

		// First step is to apply several image processing filters
		// Calls the filtering plugin
		int closingRadius = 6;
		int openingRadius = 12;
		double sigma = 4;
		ImageProcessor filtered = Fasga2MorphoFilteringPlugin
				.computeFilteredImage(image, closingRadius, openingRadius,
						sigma);
		new ImagePlus("Filtered", filtered).show();

		// Computes regions from filtered image
		// Result is a label image
		int darkRegionsThreshold = 130; 
		int redRegionThreshold = 170; 
		ImageProcessor labelImage = Fasga2ClassifyRegionsPlugin
				.computeStemRegions(filtered, darkRegionsThreshold,
						redRegionThreshold, true);
		
		// Compute morphometric features
		ResultsTable table = Fasga2QuantifySegmentedSlicePlugin.quantifyRegions(filtered, labelImage, 1);
		table.setLabel(imagePlus.getShortTitle(), table.getCounter() - 1);
		
		table.show("Fasga Results");
	}
	
	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void runOld(String arg0)
	{
		System.out.println("Start Plugin 'Quantif Fasga'");
		
		ImagePlus imagePlus = IJ.getImage();
		ImageProcessor image = imagePlus.getProcessor();

		// apply morphological filtering for removing cell wall images
		IJ.log("Start morphological filtering");
		IJ.log("   closing");
		Strel oct5 = OctagonStrel.fromRadius(6);
		DefaultAlgoListener.monitor(oct5);
		ImageProcessor filtered = Morphology.closing(image, oct5);
		IJ.log("   opening");
		Strel oct10 = OctagonStrel.fromRadius(12);
		DefaultAlgoListener.monitor(oct10);
		filtered = Morphology.opening(filtered, oct10);
		
		// apply gaussian blur radius 4
		IJ.log("smooth");
		new GaussianBlur().blurGaussian(filtered, 4, 4, .01);
		
		ImagePlus resultPlus = new ImagePlus("filtered", filtered);
		resultPlus.show();
		
		// split channels
		IJ.log("split channels");
		HashMap<String, ByteProcessor> channels = ColorImages.mapChannels(filtered);
		ByteProcessor red = channels.get("red");
		ByteProcessor blue = channels.get("blue");
		
		// un peu de morpho math pour lisser
		IJ.log("segment stem");
		// Extract bundles + sclerenchyme
		ImageProcessor darkRegions = Threshold.threshold(blue, 0, 160);

		// Add morphological processing to keep stem image
		IJ.log("fill holes");
		ImageProcessor stem = GeodesicReconstruction.fillHoles(darkRegions);
		IJ.log("morphological filtering");
		Strel sq2 = SquareStrel.fromRadius(2); 
		Strel sq4 = SquareStrel.fromRadius(4);
		stem = sq2.dilation(sq4.erosion(sq2.dilation(stem)));
		stem = BinaryImages.keepLargestRegion(stem);
		
		stem.invertLut();
		ImagePlus stemPlus = new ImagePlus("Stem", stem);
		stemPlus.show();

		IJ.log("classify regions");
		
		// Extract red area
		ImageProcessor redZone = Threshold.threshold(red, 180, 255);
		
		// combine with stem image to remove background
		constrainToMask(redZone, stem);
	
		// display as new ImagePlus
		redZone.invertLut();
		ImagePlus redZonePlus = new ImagePlus("redZone", redZone);
		redZonePlus.show();
		
		
		// combine with stem image to remove background
		constrainToMask(darkRegions, stem);
		
		// Compute rind image
		ImageProcessor rind = BinaryImages.keepLargestRegion(darkRegions);		
		
		// Compute bundles image
		ImageProcessor bundles = BinaryImages.removeLargestRegion(darkRegions);
		bundles = BinaryImages.areaOpening(bundles, 200);
		
		// show bundles image
		bundles.invertLut();
		ImagePlus bundlesPlus = new ImagePlus("bundles", bundles);
		bundlesPlus.show();

		// computes Blue region, as the union of non red and non dark
		ImageCalculator.Operation op = ImageCalculator.Operation.AND; 
		ImageProcessor blueZone = ImageCalculator.combineImages(not(redZone), not(rind), op);
		blueZone = ImageCalculator.combineImages(blueZone, not(bundles), op);
		constrainToMask(blueZone, stem);

		blueZone.invertLut();
		ImagePlus blueZonePlus = new ImagePlus("blueZone", blueZone);
		blueZonePlus.show();

		ImageProcessor overlay = stem.duplicate();
		overlay.invert();
		overlay = ColorImages.binaryOverlay(overlay, redZone, Color.MAGENTA);
		overlay = ColorImages.binaryOverlay(overlay, blueZone, new Color(0, 127, 255));
		overlay = ColorImages.binaryOverlay(overlay, rind, Color.BLACK);
		overlay = ColorImages.binaryOverlay(overlay, bundles, Color.DARK_GRAY);

		ImagePlus overlayPlus = new ImagePlus("Overlay", overlay);
		overlayPlus.show();

		
		// Area of the whole stem (for normalization) 
		double stemArea = GeometricMeasures2D.particleArea(stem, 255);
		

		// Analyze bundles -> number of bundles
		ImageProcessor bunLabels = ConnectedComponents.computeLabels(bundles, 4, 16);
		int[] labels = LabelImages.findAllLabels(bunLabels);
		int bundlesNumber = labels.length;
		
		double bundlesArea = GeometricMeasures2D.particleArea(bundles, 255);
		double bundlesFraction = bundlesArea / stemArea; 
		
		double rindArea = GeometricMeasures2D.particleArea(rind, 255);
		double rindFraction = rindArea / stemArea;
		
		double redArea = GeometricMeasures2D.particleArea(redZone, 255);
		double redFraction = redArea / stemArea;
		
		double blueArea = GeometricMeasures2D.particleArea(blueZone, 255);
		double blueFraction = blueArea / stemArea;
		
		// Calcule la couleur moyenne dans la zone "rouge" (ie, lignifiee)
		ResultsTable lignifiedColor = new AverageColorPlugin().applyTo(filtered, redZone);
		double meanRed = lignifiedColor.getValueAsDouble(0, 0);
		double meanGreen = lignifiedColor.getValueAsDouble(1, 0);
		double meanBlue = lignifiedColor.getValueAsDouble(2, 0);
				
		ResultsTable table = new ResultsTable();
		table.incrementCounter();
		table.addValue("Red Fraction", redFraction);
		table.addValue("Blue Fraction", blueFraction);
		table.addValue("Rind Fraction", rindFraction);
		table.addValue("Bundle Fraction", bundlesFraction);
		table.addValue("Bundle Number", bundlesNumber);

		table.addValue("Mean Red", meanRed);
		table.addValue("Mean Green", meanGreen);
		table.addValue("Mean Blue", meanBlue);

		table.show("Fasga Results");
	}
	

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
		
		PlugIn plugin = new QuantifFasga2Plugin();
		plugin.run("");
	}
}
