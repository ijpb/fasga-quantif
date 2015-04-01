/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

/**
 * Simple plugin that removes the black border that can arise around a color image.
 * Size of black border is measured in the middle of the image in each direction.
 * 
 * @author dlegland
 *
 */
public class RemoveBlackBorderPlugin implements PlugIn 
{

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String cmd) 
	{
		ImagePlus imagePlus = IJ.getImage();

		
		ImageProcessor result = removeBlackBorder(imagePlus.getProcessor());
		String newTitle = imagePlus.getShortTitle() + "-noBorder";
		ImagePlus resultPlus = new ImagePlus(newTitle, result);
		
		resultPlus.copyScale(imagePlus);

		resultPlus.show();
	}
	
	private ImageProcessor removeBlackBorder(ImageProcessor image) 
	{
		int width = image.getWidth();
		int height = image.getHeight();

		int xmin = 0;
		int xmax = width - 1;
		int ymin = 0;
		int ymax = height - 1;

		// Find min and max X
		int yMid = height / 2;
		while((image.get(xmin, yMid) & 0x00FFFFFF) == 0)
			xmin ++;
		while((image.get(xmax, yMid) & 0x00FFFFFF) == 0)
			xmax --;
		
		// Find min and max Y
		int xMid = width / 2;
		while((image.get(xMid, ymin) & 0x00FFFFFF) == 0)
			ymin ++;
		while((image.get(xMid, ymax) & 0x00FFFFFF) == 0)
			ymax --;
		
		// create result image 
		ImageProcessor result = image.createProcessor(xmax - xmin + 1, ymax - ymin + 1);
		for(int y = ymin; y <= ymax; y++) 
		{
			for(int x = xmin; x <= xmax; x++) 
			{
				result.set(x - xmin, y - ymin, image.get(x, y));
			}
		}
		
		return result;
	}

}
