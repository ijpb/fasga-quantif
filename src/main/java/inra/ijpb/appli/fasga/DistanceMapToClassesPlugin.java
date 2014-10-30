package inra.ijpb.appli.fasga;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

/**
 * 
 */

/**
 * Transforms a distance map into a label image with labels corresponding to
 * regions of pixels within the same relative distance interval.
 * Distance intervals are computed automatically between 0 and the maximal
 * distance within the image.
 * @author David Legland
 *
 */
public class DistanceMapToClassesPlugin implements PlugIn {

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		// Check current image 
		int[] indices = WindowManager.getIDList();
		if (indices == null) {
			IJ.error("No image", "Need at least one image to work");
			return;
		}
		
		// check image type
		ImagePlus image = IJ.getImage();
		int type = image.getType();
		if (type != ImagePlus.GRAY8 
				&& type != ImagePlus.GRAY16
				&& type != ImagePlus.GRAY32) {
			IJ.showMessage("Input image should be grayscale or float");
			return;
		}

		// Extract image size
		ImageProcessor proc = image.getProcessor();
		int width = proc.getWidth();
		int height = proc.getHeight();
		
		// create the dialog
		GenericDialog gd = new GenericDialog("Distance Classes");
		gd.addNumericField("Number of classes", 100, 0);
		gd.showDialog();
		
		// test abort
		if (gd.wasCanceled())
			return;

		// Parses arguments
		int nClasses = (int) gd.getNextNumber();
		
		// Choose default output name
		String newName = createResultImageName(image);

		// Compute max value within the mask
		double maxDist = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				maxDist = Math.max(maxDist, proc.getf(x, y));
			}
		}
		
		// Extract image size
		ImageProcessor inputProc = image.getProcessor();
		ImageProcessor resultProc = DistanceProfile.distanceMapToClasses(inputProc, nClasses);

		ImagePlus result = new ImagePlus(newName, resultProc);

		// show new image if needed
		result.show();
	}
	
	/**
	 * Compute the label image from distance image.
	 * First computes the maximum value in image, computes the appropriate
	 * width to have the desired number of classes, and associates each
	 * distance to the corresponding class.
	 */
	public Object[] exec(ImagePlus image, String newName, int nClasses) {
		// Check validity of parameters
		if (image == null) {
			System.err.println("Mask image not specified");
			return null;
		}
		
		if (newName == null)
			newName = createResultImageName(image);
	
		// Extract image size
		ImageProcessor inputProc = image.getProcessor();
		ImageProcessor resultProc = DistanceProfile.distanceMapToClasses(inputProc, nClasses);

		ImagePlus result = new ImagePlus(newName, resultProc);
		
		// create result array
		return new Object[]{newName, result};
	}

	private static String createResultImageName(ImagePlus baseImage) {
		return baseImage.getShortTitle() + "-classes";
	}
}
