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

package boofcv.alg.transform.wavelet.impl;

import boofcv.misc.AutoTypeImage;
import boofcv.misc.CodeGeneratorBase;

import java.io.FileNotFoundException;


/**
 * @author Peter Abeles
 */
public class GenerateImplWaveletTransformInner extends CodeGeneratorBase {
	String className = "ImplWaveletTransformInner";

	AutoTypeImage imageIn;
	AutoTypeImage imageOut;
	String genName;
	String sumType;
	String bitWise;
	String outputCast;

	public GenerateImplWaveletTransformInner() throws FileNotFoundException {
		setOutputFile(className);
	}

	@Override
	public void generate() throws FileNotFoundException {
		printPreamble();

		printFuncs(AutoTypeImage.F32, AutoTypeImage.F32);
		printFuncs(AutoTypeImage.S32, AutoTypeImage.S32);

		out.print("\n" +
				"}\n");
	}

	private void printPreamble() {

		out.print("import boofcv.alg.transform.wavelet.UtilWavelet;\n" +
				"import boofcv.struct.image.*;\n" +
				"import boofcv.struct.wavelet.WlCoef_F32;\n" +
				"import boofcv.struct.wavelet.WlCoef_I32;\n" +
				"\n" +
				"\n" +
				"/**\n" +
				" * <p>\n" +
				" * Standard algorithm for forward and inverse wavelet transform which has been optimized to only\n" +
				" * process the inner portion of the image by excluding the border.\n" +
				" * </p>\n" +
				" *\n" +
				" * <p>\n" +
				" * DO NOT MODIFY: This class was automatically generated by {@link GenerateImplWaveletTransformInner}\n" +
				" * </p>\n" +
				" *\n" +
				" * @author Peter Abeles\n" +
				" */\n" +
				"@SuppressWarnings({\"ForLoopReplaceableByForEach\"})\n" +
				"public class ImplWaveletTransformInner {\n\n");
	}

	private void printFuncs( AutoTypeImage imageIn , AutoTypeImage imageOut ) {
		this.imageIn = imageIn;
		this.imageOut = imageOut;

		if( imageIn.isInteger() )
			genName = "I32";
		else
			genName = "F"+imageIn.getNumBits();

		sumType = imageIn.getSumType();
		bitWise = imageIn.getBitWise();

		if( sumType.compareTo(imageOut.getDataType()) == 0 ) {
			outputCast = "";
		} else {
			outputCast = "("+imageOut.getDataType()+")";
		}

		printHorizontal();
		printVertical();
		printHorizontalInverse();
		printVerticalInverse();
	}

	private void printHorizontal() {
		out.print("\tpublic static void horizontal( WlCoef_"+genName+" coefficients , "+imageIn.getSingleBandName()+" input , "+imageOut.getSingleBandName()+" output )\n" +
				"\t{\n" +
				"\t\tfinal int offsetA = coefficients.offsetScaling;\n" +
				"\t\tfinal int offsetB = coefficients.offsetWavelet;\n" +
				"\t\tfinal "+sumType+"[] alpha = coefficients.scaling;\n" +
				"\t\tfinal "+sumType+"[] beta = coefficients.wavelet;\n" +
				"\n" +
				"\t\tfinal "+imageIn.getDataType()+" dataIn[] = input.data;\n" +
				"\t\tfinal "+imageOut.getDataType()+" dataOut[] = output.data;\n" +
				"\n" +
				"\t\tfinal int width = output.width;\n" +
				"\t\tfinal int height = input.height;\n" +
				"\t\tfinal int widthD2 = width/2;\n" +
				"\t\tfinal int startX = UtilWavelet.borderForwardLower(coefficients);\n" +
				"\t\tfinal int endOffsetX = input.width - UtilWavelet.borderForwardUpper(coefficients,input.width) - startX;\n" +
				"\n" +
				"\t\tfor( int y = 0; y < height; y++ ) {\n" +
				"\n" +
				"\t\t\tint indexIn = input.startIndex + input.stride*y + startX;\n" +
				"\t\t\tint indexOut = output.startIndex + output.stride*y + startX/2;\n" +
				"\n" +
				"\t\t\tint end = indexIn + endOffsetX;\n" +
				"\n" +
				"\t\t\tfor( ; indexIn < end; indexIn += 2 ) {\n" +
				"\n" +
				"\t\t\t\t"+sumType+" scale = 0;\n" +
				"\t\t\t\tint index = indexIn+offsetA;\n" +
				"\t\t\t\tfor( int i = 0; i < alpha.length; i++ ) {\n" +
				"\t\t\t\t\tscale += (dataIn[index++]"+bitWise+")*alpha[i];\n" +
				"\t\t\t\t}\n" +
				"\n" +
				"\t\t\t\t"+sumType+" wavelet = 0;\n" +
				"\t\t\t\tindex = indexIn+offsetB;\n" +
				"\t\t\t\tfor( int i = 0; i < beta.length; i++ ) {\n" +
				"\t\t\t\t\twavelet += (dataIn[index++]"+bitWise+")*beta[i];\n" +
				"\t\t\t\t}\n" +
				"\n");
		if( imageIn.isInteger() ) {
			out.print("\t\t\t\tscale = 2*scale/coefficients.denominatorScaling;\n" +
					"\t\t\t\twavelet = 2*wavelet/coefficients.denominatorWavelet;\n\n");
		}
		out.print("\t\t\t\tdataOut[ indexOut+widthD2] = "+outputCast+"wavelet;\n" +
				"\t\t\t\tdataOut[ indexOut++ ] = "+outputCast+"scale;\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void printVertical() {
		out.print("\tpublic static void vertical( WlCoef_"+genName+" coefficients , "+imageIn.getSingleBandName()+" input , "+imageOut.getSingleBandName()+" output )\n" +
				"\t{\n" +
				"\t\tfinal int offsetA = coefficients.offsetScaling*input.stride;\n" +
				"\t\tfinal int offsetB = coefficients.offsetWavelet*input.stride;\n" +
				"\t\tfinal "+sumType+"[] alpha = coefficients.scaling;\n" +
				"\t\tfinal "+sumType+"[] beta = coefficients.wavelet;\n" +
				"\n" +
				"\t\tfinal "+imageIn.getDataType()+" dataIn[] = input.data;\n" +
				"\t\tfinal "+imageOut.getDataType()+" dataOut[] = output.data;\n" +
				"\n" +
				"\t\tfinal int width = input.width;\n" +
				"\t\tfinal int height = output.height;\n" +
				"\t\tfinal int heightD2 = (height/2)*output.stride;\n" +
				"\t\tfinal int startY = UtilWavelet.borderForwardLower(coefficients);\n" +
				"\t\tfinal int endY = input.height - UtilWavelet.borderForwardUpper(coefficients,input.width);\n" +
				"\n" +
				"\t\tfor( int y = startY; y < endY; y += 2 ) {\n" +
				"\n" +
				"\t\t\tint indexIn = input.startIndex + input.stride*y;\n" +
				"\t\t\tint indexOut = output.startIndex + output.stride*(y/2);\n" +
				"\n" +
				"\t\t\tfor( int x = 0; x < width; x++, indexIn++) {\n" +
				"\n" +
				"\t\t\t\t"+sumType+" scale = 0;\n" +
				"\t\t\t\tint index = indexIn + offsetA;\n" +
				"\t\t\t\tfor( int i = 0; i < alpha.length; i++ ) {\n" +
				"\t\t\t\t\tscale += (dataIn[index]"+bitWise+")*alpha[i];\n" +
				"\t\t\t\t\tindex += input.stride;\n" +
				"\t\t\t\t}\n" +
				"\n" +
				"\t\t\t\t"+sumType+" wavelet = 0;\n" +
				"\t\t\t\tindex = indexIn + offsetB;\n" +
				"\t\t\t\tfor( int i = 0; i < beta.length; i++ ) {\n" +
				"\t\t\t\t\twavelet += (dataIn[index]"+bitWise+")*beta[i];\n" +
				"\t\t\t\t\tindex += input.stride;\n" +
				"\t\t\t\t}\n" +
				"\n");
		if( imageIn.isInteger() ) {
			out.print("\t\t\t\tscale = 2*scale/coefficients.denominatorScaling;\n" +
					"\t\t\t\twavelet = 2*wavelet/coefficients.denominatorWavelet;\n\n");
		}
				out.print("\t\t\t\tdataOut[indexOut+heightD2] = "+outputCast+"wavelet;\n" +
				"\t\t\t\tdataOut[indexOut++] = "+outputCast+"scale;\n" +
				"\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void printHorizontalInverse() {
		out.print("\tpublic static void horizontalInverse( WlCoef_"+genName+" coefficients , "+imageIn.getSingleBandName()+" input , "+imageOut.getSingleBandName()+" output )\n" +
				"\t{\n" +
				"\t\tfinal int offsetA = coefficients.offsetScaling;\n" +
				"\t\tfinal int offsetB = coefficients.offsetWavelet;\n" +
				"\t\tfinal "+sumType+"[] alpha = coefficients.scaling;\n" +
				"\t\tfinal "+sumType+"[] beta = coefficients.wavelet;\n" +
				"\n" +
				"\t\t"+sumType+" []trends = new "+sumType+"[ output.width ];\n" +
				"\t\t"+sumType+" []details = new "+sumType+"[ output.width ];\n" +
				"\n" +
				"\t\tfinal int width = input.width;\n" +
				"\t\tfinal int height = output.height;\n" +
				"\t\tfinal int widthD2 = width/2;\n" +
				"\t\tfinal int lowerBorder = UtilWavelet.borderForwardLower(coefficients);\n" +
				"\t\tfinal int upperBorder = output.width - UtilWavelet.borderForwardUpper(coefficients,output.width);" +
				"\n");
		if( imageIn.isInteger() ) {
			out.print("\t\tfinal int e = coefficients.denominatorScaling*2;\n" +
					"\t\tfinal int f = coefficients.denominatorWavelet*2;\n" +
					"\t\tfinal int ef = e*f;\n" +
					"\t\tfinal int ef2 = ef/2;\n" +
					"\n");
		}

		out.print("\t\tfor( int y = 0; y < height; y++ ) {\n" +
				"\n" +
				"\t\t\t// initialize details and trends arrays\n" +
				"\t\t\tint indexSrc = input.startIndex + y*input.stride+lowerBorder/2;\n" +
				"\t\t\tfor( int x = lowerBorder; x < upperBorder; x += 2 , indexSrc++ ) {\n" +
				"\t\t\t\t"+sumType+" a = input.data[ indexSrc ] "+bitWise+";\n" +
				"\t\t\t\t"+sumType+" d = input.data[ indexSrc + widthD2 ] "+bitWise+";\n" +
				"\n" +
				"\t\t\t\t// add the trend\n" +
				"\t\t\t\tfor( int i = 0; i < 2; i++ )\n" +
				"\t\t\t\t\ttrends[i+x+offsetA] = a*alpha[i];\n" +
				"\n" +
				"\t\t\t\t// add the detail signal\n" +
				"\t\t\t\tfor( int i = 0; i < 2; i++ )\n" +
				"\t\t\t\t\tdetails[i+x+offsetB] = d*beta[i];\n" +
				"\t\t\t}\n" +
				"\n" +
				"\t\t\tfor( int i = upperBorder+offsetA; i < upperBorder; i++ )\n" +
				"\t\t\t\ttrends[i] = 0;\n" +
				"\t\t\tfor( int i = upperBorder+offsetB; i < upperBorder; i++ )\n" +
				"\t\t\t\tdetails[i] = 0;\n"+
				"\n"+
				"\t\t\t// perform the normal inverse transform\n" +
				"\t\t\tindexSrc = input.startIndex + y*input.stride+lowerBorder/2;\n" +
				"\t\t\tfor( int x = lowerBorder; x < upperBorder; x += 2 , indexSrc++ ) {\n" +
				"\t\t\t\t"+sumType+" a = input.data[ indexSrc ] "+bitWise+";\n" +
				"\t\t\t\t"+sumType+" d = input.data[ indexSrc + widthD2 ] "+bitWise+";\n" +
				"\n" +
				"\t\t\t\t// add the trend\n" +
				"\t\t\t\tfor( int i = 2; i < alpha.length; i++ ) {\n" +
				"\t\t\t\t\ttrends[i+x+offsetA] += a*alpha[i];\n" +
				"\t\t\t\t}\n" +
				"\n" +
				"\t\t\t\t// add the detail signal\n" +
				"\t\t\t\tfor( int i = 2; i < beta.length; i++ ) {\n" +
				"\t\t\t\t\tdetails[i+x+offsetB] += d*beta[i];\n" +
				"\t\t\t\t}\n" +
				"\t\t\t}\n" +
				"\n" +
				"\t\t\tint indexDst = output.startIndex + y*output.stride + lowerBorder;\n" +
				"\t\t\tfor( int x = lowerBorder; x < upperBorder; x++ ) {\n");

		if( imageIn.isInteger() ) {
			out.print("\t\t\t\toutput.data[ indexDst++ ] = "+outputCast+"UtilWavelet.round(trends[x]*f + details[x]*e , ef2,ef);\n" );
		} else {
			out.print("\t\t\t\toutput.data[ indexDst++ ] = "+outputCast+"(trends[x] + details[x]);\n" );
		}

		out.print("\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void printVerticalInverse() {
		out.print("\tpublic static void verticalInverse( WlCoef_"+genName+" coefficients , "+imageIn.getSingleBandName()+" input , "+imageOut.getSingleBandName()+" output )\n" +
				"\t{\n" +
				"\t\tfinal int offsetA = coefficients.offsetScaling;\n" +
				"\t\tfinal int offsetB = coefficients.offsetWavelet;\n" +
				"\t\tfinal "+sumType+"[] alpha = coefficients.scaling;\n" +
				"\t\tfinal "+sumType+"[] beta = coefficients.wavelet;\n" +
				"\n" +
				"\t\t"+sumType+" []trends = new "+sumType+"[ output.height ];\n" +
				"\t\t"+sumType+" []details = new "+sumType+"[ output.height ];\n" +
				"\n" +
				"\t\tfinal int width = output.width;\n" +
				"\t\tfinal int height = input.height;\n" +
				"\t\tfinal int heightD2 = (height/2)*input.stride;\n" +
				"\t\tfinal int lowerBorder = UtilWavelet.borderForwardLower(coefficients);\n" +
				"\t\tfinal int upperBorder = output.height - UtilWavelet.borderForwardUpper(coefficients,output.height);" +
				"\n");

		if( imageIn.isInteger() ) {
			out.print("\t\tfinal int e = coefficients.denominatorScaling*2;\n" +
					"\t\tfinal int f = coefficients.denominatorWavelet*2;\n" +
					"\t\tfinal int ef = e*f;\n" +
					"\t\tfinal int ef2 = ef/2;\n" +
					"\n");
		}
		out.print("\t\tfor( int x = 0; x < width; x++) {\n" +
				"\n" +
				"\t\t\tint indexSrc = input.startIndex + (lowerBorder/2)*input.stride + x;\n" +
				"\t\t\tfor( int y = lowerBorder; y < upperBorder; y += 2 , indexSrc += input.stride ) {\n" +
				"\t\t\t\t"+sumType+" a = input.data[ indexSrc ] "+bitWise+";\n" +
				"\t\t\t\t"+sumType+" d = input.data[ indexSrc + heightD2 ] "+bitWise+";\n" +
				"\n" +
				"\t\t\t\t// add the trend\n" +
				"\t\t\t\tfor( int i = 0; i < 2; i++ )\n" +
				"\t\t\t\t\ttrends[i+y+offsetA] = a*alpha[i];\n" +
				"\n" +
				"\t\t\t\t// add the detail signal\n" +
				"\t\t\t\tfor( int i = 0; i < 2; i++ )\n" +
				"\t\t\t\t\tdetails[i+y+offsetB] = d*beta[i];\n" +
				"\t\t\t}\n" +
				"\n" +
				"\t\t\tfor( int i = upperBorder+offsetA; i < upperBorder; i++ )\n" +
				"\t\t\t\ttrends[i] = 0;\n" +
				"\t\t\tfor( int i = upperBorder+offsetB; i < upperBorder; i++ )\n" +
				"\t\t\t\tdetails[i] = 0;\n"+
				"\n"+
				"\t\t\t// perform the normal inverse transform\n" +
				"\t\t\tindexSrc = input.startIndex + (lowerBorder/2)*input.stride + x;\n" +
				"\n" +
				"\t\t\tfor( int y = lowerBorder; y < upperBorder; y += 2 , indexSrc += input.stride ) {\n" +
				"\t\t\t\t"+sumType+" a = input.data[indexSrc] "+bitWise+";\n" +
				"\t\t\t\t"+sumType+" d = input.data[indexSrc+heightD2] "+bitWise+";\n" +
				"\n" +
				"\t\t\t\t// add the 'average' signal\n" +
				"\t\t\t\tfor( int i = 2; i < alpha.length; i++ ) {\n" +
				"\t\t\t\t\ttrends[y+offsetA+i] += a*alpha[i];\n" +
				"\t\t\t\t}\n" +
				"\n" +
				"\t\t\t\t// add the detail signal\n" +
				"\t\t\t\tfor( int i = 2; i < beta.length; i++ ) {\n" +
				"\t\t\t\t\tdetails[y+offsetB+i] += d*beta[i];\n" +
				"\t\t\t\t}\n" +
				"\t\t\t}\n" +
				"\n" +
				"\t\t\tint indexDst = output.startIndex + x + lowerBorder*output.stride;\n" +
				"\t\t\tfor( int y = lowerBorder; y < upperBorder; y++ , indexDst += output.stride ) {\n");
		if( imageIn.isInteger() ) {
			out.print("\t\t\t\toutput.data[ indexDst ] = "+outputCast+"UtilWavelet.round(trends[y]*f + details[y]*e , ef2 , ef);\n" );
		} else {
			out.print("\t\t\t\toutput.data[ indexDst ] = "+outputCast+"(trends[y] + details[y]);\n" );
		}
		out.print("\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	public static void main( String args[] ) throws FileNotFoundException {
		GenerateImplWaveletTransformInner app = new GenerateImplWaveletTransformInner();
		app.generate();
	}
}
