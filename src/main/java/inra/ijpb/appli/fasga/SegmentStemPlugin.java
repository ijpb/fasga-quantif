package inra.ijpb.appli.fasga;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.segment.Threshold;

/**
 * Segment a maize stem in a Color or grayscale image.
 * 
 * @author David Legland
 *
 */
public class SegmentStemPlugin implements PlugIn 
{
	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) 
	{
		// Check an image was open
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null) 
		{
			IJ.error("No image", "Need at least one image to work");
			return;
		}

		// Execute core of the plugin
		ImagePlus res = exec(image);

		if (res == null)
			return;

		// Display the result image
		res.show();
	}

	public ImagePlus exec(ImagePlus image)
	{
		// extract processor of input image
		ImageProcessor ip = image.getProcessor();
		
		// compute stem segmentation
		ImageProcessor ip2 = segmentStem(ip);
				
		// create new image
		String name = image.getShortTitle() + "-stem";
		ImagePlus result = new ImagePlus(name, ip2);
		return result;
	}
	
	/**
	 * Computes the binary image that correspond to the stem.
	 * A dark object appearing on a light background is expected.
	 * 
	 * @param image input image (converted to byte if needed)
	 * @return binary image processor corresponding to the stem
	 */
	public static final ImageProcessor segmentStem(ImageProcessor image) 
	{
		// convert to byte if needed
		ImageProcessor res;
		if (image instanceof ByteProcessor) 
		{
			res = (ByteProcessor) image.duplicate();
		}
		else 
		{
			res = (ByteProcessor) image.convertToByte(false);
		}
		
		// compute some statistics, including histogram
		ImageStatistics stats = res.getStatistics();
		
		// select Otsu auto-threshold method
		AutoThresholder thresholder = new AutoThresholder();
		AutoThresholder.Method method = AutoThresholder.Method.Otsu;
		
		// compute threshold
		int threshold = thresholder.getThreshold(method, stats.histogram);
		
		res = Threshold.threshold(res, 0, threshold);
//		// compute image dimension
//		int w = image.getWidth();
//		int h = image.getHeight();
//
//		// apply threshold to image
//		for (int i = 0; i < w * h; i++) {
//			if (res.get(i) >= threshold)
//				res.set(i, 0);
//			else
//				res.set(i, 255);
//		}

		// fill holes in image
		res = GeodesicReconstruction.fillHoles(res);

		// Set inverted display
		if (!res.isInvertedLut())
			res.invertLut();

		return res;
	}
	
}
