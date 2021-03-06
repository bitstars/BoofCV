/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.tracker.circulant;

import boofcv.abst.transform.fft.DiscreteFourierTransform;
import boofcv.alg.misc.PixelMath;
import boofcv.alg.transform.fft.DiscreteFourierTransformOps;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageFloat64;
import boofcv.struct.image.InterleavedF64;
import georegression.struct.shapes.Rectangle2D_I32;

/**
 * <p>
 * Tracker that uses the theory of Circulant matrices, Discrete Fourier Transform (DCF), and linear classifiers to track
 * a target and learn its changes in appearance [1].  The target is assumed to be rectangular and has fixed size and
 * location.  A dense local search is performed around the most recent target location.  The search is done quickly
 * using the DCF.
 * </p>
 *
 * <p>
 * Tracking is performed using texture information.  Since only one description of the target is saved, tracks can
 * drift over time.  Tracking performance seems to improve if the object has distinctive edges and that's included
 * in the track region.
 * </p>
 *
 * <p>
 * [1] Henriques, Joao F., et al. "Exploiting the circulant structure of tracking-by-detection with kernels."
 * Computer Vision–ECCV 2012. Springer Berlin Heidelberg, 2012. 702-715.
 * </p>
 *
 * @author Peter Abeles
 */
public class CirculantTrackerOrig {

	// --- Tuning parameters
	// spatial bandwidth (proportional to target)
	private double output_sigma_factor;

	// gaussian kernel bandwidth
	private double sigma;

	// regularization term
	private double lambda;
	// linear interpolation term.  Adjusts how fast it can learn
	private double interp_factor;

	// the maximum pixel value
	private double maxPixelValue;

	// extra padding around the selected region
	private double padding = 1;

	//----- Internal variables

	// computes the FFT
	private DiscreteFourierTransform<ImageFloat64,InterleavedF64> fft = DiscreteFourierTransformOps.createTransformF64();

	// storage for subimage of input image
	protected ImageFloat64 subInput = new ImageFloat64(1,1);
	// storage for the subimage of the previous frame
	protected ImageFloat64 subPrev = new ImageFloat64(1,1);

	// cosine window used to reduce artifacts from FFT
	protected ImageFloat64 cosine = new ImageFloat64(1,1);

	// Storage for the kernel's response
	private ImageFloat64 k = new ImageFloat64(1,1);
	private InterleavedF64 kf = new InterleavedF64(1,1,2);

	// Learn values.  used to compute weight in linear classifier
	private InterleavedF64 alphaf = new InterleavedF64(1,1,2);
	private InterleavedF64 newAlphaf = new InterleavedF64(1,1,2);

	// location of target
	protected Rectangle2D_I32 regionTrack = new Rectangle2D_I32();
	protected Rectangle2D_I32 regionOut = new Rectangle2D_I32();

	// Used for computing the gaussian kernel
	protected ImageFloat64 gaussianWeight = new ImageFloat64(1,1);
	protected InterleavedF64 gaussianWeightDFT = new InterleavedF64(1,1,2);

	// storage for storing temporary results
	private ImageFloat64 tmpReal0 = new ImageFloat64(1,1);
	private ImageFloat64 tmpReal1 = new ImageFloat64(1,1);

	private InterleavedF64 tmpFourier0 = new InterleavedF64(1,1,2);
	private InterleavedF64 tmpFourier1 = new InterleavedF64(1,1,2);
	private InterleavedF64 tmpFourier2 = new InterleavedF64(1,1,2);

	/**
	 * Configure tracker
	 *
	 * @param output_sigma_factor  spatial bandwidth (proportional to target) Try 1.0/16.0
	 * @param sigma Sigma for Gaussian kernel in linear classifier.  Try 0.2f
	 * @param lambda Try 1e-2f
	 * @param interp_factor Try 0.075f
	 * @param maxPixelValue Maximum pixel value.  Typically 255
	 */
	public CirculantTrackerOrig(double output_sigma_factor, double sigma, double lambda, double interp_factor,
								double maxPixelValue) {
		this.output_sigma_factor = output_sigma_factor;
		this.sigma = sigma;
		this.lambda = lambda;
		this.interp_factor = interp_factor;
		this.maxPixelValue = maxPixelValue;
	}

	/**
	 * Initializes tracking around the specified rectangle region
	 * @param image Image to start tracking from
	 * @param x0 top-left corner of region
	 * @param y0 top-left corner of region
	 * @param regionWidth region's width
	 * @param regionHeight region's height
	 */
	public void initialize( ImageFloat32 image , int x0 , int y0 , int regionWidth , int regionHeight ) {

		regionOut.width = regionWidth;
		regionOut.height = regionHeight;

		// adjust for padding
		int w = (int)(regionWidth*(1+padding));
		int h = (int)(regionHeight*(1+padding));
		int cx = x0 + regionWidth/2;
		int cy = y0 + regionHeight/2;

		// save the track location
		this.regionTrack.width = w;
		this.regionTrack.height = h;
		this.regionTrack.tl_x = cx-w/2;
		this.regionTrack.tl_y = cy-h/2;

		ensureInBounds(regionTrack,image.width,image.height);

		initializeData(image);
		initialLearning(image, regionTrack.tl_x, regionTrack.tl_y);
	}

	/**
	 * Declare and compute various data structures
	 */
	protected void initializeData(ImageBase image ) {
		boolean sizeChange = cosine.width != regionTrack.width || cosine.height != regionTrack.height;
		if( sizeChange ) {
			if( regionTrack.width > image.width || regionTrack.height > image.height )
				throw new IllegalArgumentException("Specified target is larger than the input image!");
			resizeImages(regionTrack.width, regionTrack.height);
			computeCosineWindow(cosine);
			computeGaussianWeights();
		}
	}

	/**
	 * Learn the target's appearance.
	 */
	protected void initialLearning( ImageFloat32 image , int x0 , int y0 ) {
		// get subwindow at current estimated target position, to train classifer
		get_subwindow(image, x0, y0, subInput);

		// Kernel Regularized Least-Squares, calculate alphas (in Fourier domain)
		//	k = dense_gauss_kernel(sigma, x);
		dense_gauss_kernel(sigma,subInput,subInput,k);
		fft.forward(k, kf);

//		gaussianWeight.print("%7.5f");
//		System.out.println("----------------");
//		gaussianWeightDFT.print("%7.5f");
//		System.out.println("----------------");
//		k.print("%7.5f");
//		System.out.println("----------------");

		// new_alphaf = yf ./ (fft2(k) + lambda);   %(Eq. 7)
		computeAlphas(gaussianWeightDFT, kf, lambda, alphaf);

//		fft.inverse(alphaf, tmpReal0);
//		tmpReal0.print("%7.5f");

		ImageFloat64 tmp = subInput;
		subInput = subPrev;
		subPrev = tmp;
	}

	/**
	 * Computes the cosine window
	 */
	protected static void computeCosineWindow( ImageFloat64 cosine ) {
		double cosX[] = new double[ cosine.width ];
		for( int x = 0; x < cosine.width; x++ ) {
			cosX[x] = 0.5*(1 - Math.cos( 2.0*Math.PI*x/(cosine.width-1) ));
		}
		for( int y = 0; y < cosine.height; y++ ) {
			int index = cosine.startIndex + y*cosine.stride;
			double cosY = 0.5*(1 - Math.cos( 2.0*Math.PI*y/(cosine.height-1) ));
			for( int x = 0; x < cosine.width; x++ ) {
				cosine.data[index++] = cosX[x]*cosY;
			}
		}
	}

	/**
	 * Computes the weights used in the gaussian kernel
	 */
	protected void computeGaussianWeights() {
		// desired output (gaussian shaped), bandwidth proportional to target size
		double output_sigma = Math.sqrt(gaussianWeight.width*gaussianWeight.height) * output_sigma_factor;

		double left = -0.5/(output_sigma*output_sigma);

		int radiusX = gaussianWeight.width/2;
		int radiusY = gaussianWeight.height/2;

		for( int y = 0; y < gaussianWeight.height; y++ ) {
			int index = gaussianWeight.startIndex + y*gaussianWeight.stride;

			double ry = y-radiusY;

			for( int x = 0; x < gaussianWeight.width; x++ ) {
				double rx = x-radiusX;

				gaussianWeight.data[index++] = Math.exp(left * (ry * ry + rx * rx));
			}
		}

		fft.forward(gaussianWeight, gaussianWeightDFT);
	}

	protected void resizeImages(int width, int height) {
		subInput.reshape(width,height);
		subPrev.reshape(width,height);
		cosine.reshape(width,height);
		k.reshape(width,height);
		kf.reshape(width,height);
		alphaf.reshape(width,height);
		newAlphaf.reshape(width,height);
		tmpReal0.reshape(width,height);
		tmpReal1.reshape(width,height);
		tmpFourier0.reshape(width,height);
		tmpFourier1.reshape(width,height);
		tmpFourier2.reshape(width,height);
		gaussianWeight.reshape(width,height);
		gaussianWeightDFT.reshape(width,height);
	}

	/**
	 * Search for the track in the image and
	 *
	 * @param image Next image in the sequence
	 */
	public void performTracking( ImageFloat32 image ) {
		updateTrackLocation(image);
		if( interp_factor != 0 )
			performLearning(image);
	}

	/**
	 * Find the target inside the current image by searching around its last known location
	 */
	protected void updateTrackLocation(ImageFloat32 image) {
		get_subwindow(image, regionTrack.tl_x, regionTrack.tl_y, subInput);

		// calculate response of the classifier at all locations
		// matlab: k = dense_gauss_kernel(sigma, x, z);
		dense_gauss_kernel(sigma,subInput,subPrev,k);

		fft.forward(k,kf);

		// response = real(ifft2(alphaf .* fft2(k)));   %(Eq. 9)
		DiscreteFourierTransformOps.multiplyComplex(alphaf, kf, tmpFourier0);
		fft.inverse(tmpFourier0, tmpReal0);

		// find the pixel with the largest response
		int N = tmpReal0.width*tmpReal0.height;
		int indexBest = -1;
		double valueBest = -1;
		for( int i = 0; i < N; i++ ) {
			double v = tmpReal0.data[i];
			if( v > valueBest ) {
				valueBest = v;
				indexBest = i;
			}
		}

		// peak in region's coordinate system
		int deltaX = (indexBest % tmpReal0.width) - subInput.width/2;
		int deltaY = (indexBest / tmpReal0.width) - subInput.height/2;

		// convert peak location into image coordinate system
		regionTrack.tl_x = regionTrack.tl_x + deltaX;
		regionTrack.tl_y = regionTrack.tl_y + deltaY;

		ensureInBounds(regionTrack,image.width,image.height);

		regionOut.tl_x = (regionTrack.tl_x+regionTrack.width/2)-regionOut.width/2;
		regionOut.tl_y = (regionTrack.tl_y+regionTrack.height/2)-regionOut.height/2;
	}

	/**
	 * Update the alphas and the track's appearance
	 */
	public void performLearning(ImageFloat32 image) {
		// use the update track location
		get_subwindow(image, regionTrack.tl_x, regionTrack.tl_y, subInput);

		// Kernel Regularized Least-Squares, calculate alphas (in Fourier domain)
		//	k = dense_gauss_kernel(sigma, x);
		dense_gauss_kernel(sigma, subInput, subInput, k);
		fft.forward(k,kf);

		// new_alphaf = yf ./ (fft2(k) + lambda);   %(Eq. 7)
		computeAlphas(gaussianWeightDFT, kf, lambda, newAlphaf);

		// subsequent frames, interpolate model
		// alphaf = (1 - interp_factor) * alphaf + interp_factor * new_alphaf;
		int N = alphaf.width*alphaf.height*2;
		for( int i = 0; i < N; i++ ) {
			alphaf.data[i] = (1-interp_factor)*alphaf.data[i] + interp_factor*newAlphaf.data[i];
		}

		// Set the previous image to be an interpolated version
		//		z = (1 - interp_factor) * z + interp_factor * new_z;
		N = subInput.width*subInput.height;
		for( int i = 0; i < N; i++ ) {
			subPrev.data[i] = (1-interp_factor)*subPrev.data[i] + interp_factor*subInput.data[i];
		}
	}

	/**
	 * Gaussian Kernel with dense sampling.
	 *  Evaluates a gaussian kernel with bandwidth SIGMA for all displacements
	 *  between input images X and Y, which must both be MxN. They must also
	 *  be periodic (ie., pre-processed with a cosine window). The result is
	 *  an MxN map of responses.
	 *
	 * @param sigma Gaussian kernel bandwidth
	 * @param x Input image
	 * @param y Input image
	 * @param k Output containing Gaussian kernel for each element in target region
	 */
	public void dense_gauss_kernel( double sigma , ImageFloat64 x , ImageFloat64 y , ImageFloat64 k ) {

		InterleavedF64 xf=tmpFourier0,yf,xyf=tmpFourier2;
		ImageFloat64 xy = tmpReal0;
		double yy;

		// find x in Fourier domain
		fft.forward(x, xf);
		double xx = imageDotProduct(x);

		if( x != y ) {
			// general case, x and y are different
			yf = tmpFourier1;
			fft.forward(y,yf);
			yy = imageDotProduct(y);
		} else {
			// auto-correlation of x, avoid repeating a few operations
			yf = xf;
			yy = xx;
		}

		//----   xy = invF[ F(x)*F(y) ]
		// cross-correlation term in Fourier domain
		elementMultConjB(xf,yf,xyf);
		// convert to spatial domain
		fft.inverse(xyf,xy);
		circshift(xy,tmpReal1);

		// calculate gaussian response for all positions
		gaussianKernel(xx, yy, tmpReal1, sigma, k);
	}

	public static void circshift( ImageFloat64 a, ImageFloat64 b ) {
		int w2 = a.width/2;
		int h2 = b.height/2;

		for( int y = 0; y < a.height; y++ ) {
			int yy = (y+h2)%a.height;

			for( int x = 0; x < a.width; x++ ) {
				int xx = (x+w2)%a.width;

				b.set( xx , yy , a.get(x,y));
			}
		}

	}

	/**
	 * Computes the dot product of the image with itself
	 */
	public static double imageDotProduct(ImageFloat64 a) {

		double total = 0;

		int N = a.width*a.height;
		for( int index = 0; index < N; index++ ) {
			double value = a.data[index];
			total += value*value;
		}

		return total;
	}

	/**
	 * Element-wise multiplication of 'a' and the complex conjugate of 'b'
	 */
	public static void elementMultConjB( InterleavedF64 a , InterleavedF64 b , InterleavedF64 output ) {
		for( int y = 0; y < a.height; y++ ) {

			int index = a.startIndex + y*a.stride;

			for( int x = 0; x < a.width; x++, index += 2 ) {

				double realA = a.data[index];
				double imgA = a.data[index+1];
				double realB = b.data[index];
				double imgB = b.data[index+1];

				output.data[index] = realA*realB + imgA*imgB;
				output.data[index+1] = -realA*imgB + imgA*realB;
			}
		}
	}

	/**
	 * new_alphaf = yf ./ (fft2(k) + lambda);   %(Eq. 7)
	 */
	protected static void computeAlphas( InterleavedF64 yf , InterleavedF64 kf , double lambda ,
										 InterleavedF64 alphaf ) {

		for( int y = 0; y < kf.height; y++ ) {

			int index = yf.startIndex + y*yf.stride;

			for( int x = 0; x < kf.width; x++, index += 2 ) {
				double a = yf.data[index];
				double b = yf.data[index+1];

				double c = kf.data[index] + lambda;
				double d = kf.data[index+1];

				double bottom = c*c + d*d;

				alphaf.data[index] = (a*c + b*d)/bottom;
				alphaf.data[index+1] = (b*c - a*d)/bottom;
			}
		}
	}

	/**
	 * Computes the output of the Gaussian kernel for each element in the target region
	 *
	 * k = exp(-1 / sigma^2 * max(0, (xx + yy - 2 * xy) / numel(x)));
	 *
	 * @param xx ||x||^2
	 * @param yy ||y||^2
	 */
	protected static void gaussianKernel( double xx , double yy , ImageFloat64 xy , double sigma  , ImageFloat64 output ) {
		double sigma2 = sigma*sigma;
		double N = xy.width*xy.height;

		for( int y = 0; y < xy.height; y++ ) {
			int index = xy.startIndex + y*xy.stride;

			for( int x = 0; x < xy.width; x++ , index++ ) {

				// (xx + yy - 2 * xy) / numel(x)
				double value = (xx + yy - 2f*xy.data[index])/N;

				double v = Math.exp(-Math.max(0, value) / sigma2);

				output.data[index] = v;
			}
		}
	}

	/**
	 * Copies the target into the output image and applies the cosine window to it.
	 */
	protected void get_subwindow( ImageFloat32 image , int x0 , int y0 , ImageFloat64 output ) {
		// copy the target
		for (int y = 0; y < regionTrack.height; y++) {
			int indexSrc = image.startIndex + (y0 + y) * image.stride + x0;
			int indexDst = output.startIndex + y * output.stride;

			for (int x = 0; x < regionTrack.width; x++) {
				output.data[indexDst++] = image.data[indexSrc++];
			}
		}
		// normalize values to be from -0.5 to 0.5
		PixelMath.divide(output, maxPixelValue, output);
		PixelMath.plus(output, -0.5f, output);
		// apply the cosine window to it
		PixelMath.multiply(output,cosine,output);
	}

	/**
	 * Makes sure the specified region is inside the image bounds
	 */
	protected static void ensureInBounds( Rectangle2D_I32 region , int imgWidth , int imgHeight ) {
		if( region.tl_x < 0 )
			region.tl_x = 0;
		else if( region.tl_x > imgWidth-region.width )
			region.tl_x = imgWidth-region.width;
		if( region.tl_y < 0 )
			region.tl_y = 0;
		else if( region.tl_y > imgHeight-region.height )
			region.tl_y = imgHeight-region.height;
	}


	/**
	 * The location of the target in the image
	 */
	public Rectangle2D_I32 getTargetLocation() {
		return regionOut;
	}

	/**
	 * Visual appearance of the target
	 */
	public ImageFloat64 getTargetTemplate() {
		return subInput;
	}
}
