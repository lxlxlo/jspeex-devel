/******************************************************************************
 *                                                                            *
 * Copyright (c) 1999-2003 Wimba S.A., All Rights Reserved.                   *
 *                                                                            *
 * COPYRIGHT:                                                                 *
 *      This software is the property of Wimba S.A.                           *
 *      This software is redistributed under the Xiph.org variant of          *
 *      the BSD license.                                                      *
 *      Redistribution and use in source and binary forms, with or without    *
 *      modification, are permitted provided that the following conditions    *
 *      are met:                                                              *
 *      - Redistributions of source code must retain the above copyright      *
 *      notice, this list of conditions and the following disclaimer.         *
 *      - Redistributions in binary form must reproduce the above copyright   *
 *      notice, this list of conditions and the following disclaimer in the   *
 *      documentation and/or other materials provided with the distribution.  *
 *      - Neither the name of Wimba, the Xiph.org Foundation nor the names of *
 *      its contributors may be used to endorse or promote products derived   *
 *      from this software without specific prior written permission.         *
 *                                                                            *
 * WARRANTIES:                                                                *
 *      This software is made available by the authors in the hope            *
 *      that it will be useful, but without any warranty.                     *
 *      Wimba S.A. is not liable for any consequence related to the           *
 *      use of the provided software.                                         *
 *                                                                            *
 * Class: SpeexAudioFileReader.java                                           *
 *                                                                            *
 * Author: Marc GIMPEL                                                        *
 *                                                                            *
 * Date: 12th July 2003                                                       *
 *                                                                            *
 ******************************************************************************/

/* $Id$ */

package org.xiph.speex.spi;

import  java.io.File;
import  java.io.InputStream;
import  java.io.IOException;
import  java.io.DataInputStream;
import  java.io.FileInputStream;
import  java.io.SequenceInputStream;
import  java.io.ByteArrayInputStream;
import  java.io.ByteArrayOutputStream;
import  java.net.URL;

import  javax.sound.sampled.AudioSystem;
import  javax.sound.sampled.AudioFormat;
import  javax.sound.sampled.AudioFileFormat;
import  javax.sound.sampled.AudioInputStream;
import  javax.sound.sampled.UnsupportedAudioFileException;
import  javax.sound.sampled.spi.AudioFileReader;

import  org.xiph.speex.OggCrc;

/**
 * Provider for Speex audio file reading services.
 * This implementation can parse the format information from Speex audio file,
 * and can produce audio input streams from files of this type.
 * 
 * @author Marc Gimpel, Wimba S.A. (marc@wimba.com)
 * @version $Revision$
 */
public class SpeexAudioFileReader
  extends AudioFileReader
{
  public static final int    OGG_HEADERSIZE   = 27;
  public static final int    SPEEX_HEADERSIZE = 80;
  public static final int    SEGOFFSET        = 26;
  public static final String OGGID            = "OggS";
  public static final String SPEEXID          = "Speex   ";

  /**
   * Obtains the audio file format of the File provided.
   * The File must point to valid audio file data.
   * @param file the File from which file format information should be extracted.
   * @return an AudioFileFormat object describing the audio file format.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  public AudioFileFormat getAudioFileFormat(File file)
    throws UnsupportedAudioFileException, IOException
  {
    InputStream inputStream = null;
    try {
      inputStream = new FileInputStream(file);
      return getAudioFileFormat(inputStream, (int) file.length());
    }
    finally {
      inputStream.close();
    }
  }

  /**
   * Obtains an audio input stream from the URL provided.
   * The URL must point to valid audio file data.
   * @param url the URL for which the AudioInputStream should be constructed.
   * @return an AudioInputStream object based on the audio file data pointed to by the URL.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  public AudioFileFormat getAudioFileFormat(URL url)
    throws UnsupportedAudioFileException, IOException
  {
    InputStream inputStream = url.openStream();
    try {
      return getAudioFileFormat(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  /**
   * Obtains an audio input stream from the input stream provided.
   * @param stream the input stream from which the AudioInputStream should be constructed.
   * @return an AudioInputStream object based on the audio file data contained in the input stream.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  public AudioFileFormat getAudioFileFormat(InputStream stream)
    throws UnsupportedAudioFileException, IOException
  {
    return getAudioFileFormat(stream, AudioSystem.NOT_SPECIFIED);
  }

  /**
   * Return the AudioFileFormat from the given InputStream.
   * @param stream the input stream from which the AudioInputStream should be constructed.
   * @param medialength
   * @return an AudioInputStream object based on the audio file data contained in the input stream.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  protected AudioFileFormat getAudioFileFormat(InputStream stream, int medialength)
    throws UnsupportedAudioFileException, IOException
  {
    return getAudioFileFormat(stream, null, medialength);
  }

  /**
   * Return the AudioFileFormat from the given InputStream. Implementation.
   * @param bitStream
   * @param baos
   * @param medialength
   * @return an AudioInputStream object based on the audio file data contained in the input stream.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  protected AudioFileFormat getAudioFileFormat(InputStream bitStream, ByteArrayOutputStream baos, int mediaLength)
    throws UnsupportedAudioFileException, IOException
  {
    AudioFormat format;
    try {
      int sampleRate = 0;
      int channels   = 0;
      byte[] header  = new byte[128];
      int segments = 0;
      int bodybytes = 0; 
      DataInputStream dis = new DataInputStream(bitStream);
      if (baos == null)
        baos = new ByteArrayOutputStream(128);
      int origchksum;
      int chksum;
      // read the OGG header
      dis.readFully(header, 0, OGG_HEADERSIZE);
      baos.write(header, 0, OGG_HEADERSIZE);
      origchksum = bytestoint(header, 22);
      header[22] = 0;
      header[23] = 0;
      header[24] = 0;
      header[25] = 0;
      chksum=OggCrc.checksum(0, header, 0, OGG_HEADERSIZE);
      // make sure its a OGG header
      if (!OGGID.equals(new String(header, 0, 4))) {
        throw new UnsupportedAudioFileException("missing ogg id!");
      }
      // how many segments are there?
      segments = header[SEGOFFSET] & 0xFF;
      if (segments > 1) {
        throw new UnsupportedAudioFileException("Corrupt Speex Header: more than 1 segments");
      }
      dis.readFully(header, OGG_HEADERSIZE, segments);
      baos.write(header, OGG_HEADERSIZE, segments);
      chksum=OggCrc.checksum(chksum, header, OGG_HEADERSIZE, segments);
      // get the number of bytes in the segment
      bodybytes = header[OGG_HEADERSIZE] & 0xFF;
      if (bodybytes!=SPEEX_HEADERSIZE) {
        throw new UnsupportedAudioFileException("Corrupt Speex Header: size=" + bodybytes);
      }
      // read the Speex header
      dis.readFully(header, OGG_HEADERSIZE+1, bodybytes);
      baos.write(header, OGG_HEADERSIZE+1, bodybytes);
      chksum=OggCrc.checksum(chksum, header, OGG_HEADERSIZE+1, bodybytes);
      // make sure its a Speex header
      if (!SPEEXID.equals(new String(header, OGG_HEADERSIZE+1, 8))) {
        throw new UnsupportedAudioFileException("Corrupt Speex Header: missing Speex ID");
      }
//      mode       = payload[HEADERSIZE+1+40] & 0xFF;
      sampleRate = bytestoint(header, OGG_HEADERSIZE+1+36);
      channels   = bytestoint(header, OGG_HEADERSIZE+1+48);
      // Checksum
      if (chksum != origchksum)
        throw new IOException("Ogg CheckSums do not match");
      format = new AudioFormat(SpeexEncoding.SPEEX, (float)sampleRate, AudioSystem.NOT_SPECIFIED, channels, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, false);
    }
    catch (IOException ioe) {
      throw new UnsupportedAudioFileException(ioe.getMessage());
    }
    return new AudioFileFormat(SpeexFileFormatType.SPEEX, format, AudioSystem.NOT_SPECIFIED);
  }

  /**
   * Obtains an audio input stream from the File provided.
   * The File must point to valid audio file data.
   * @param file the File for which the AudioInputStream should be constructed.
   * @return an AudioInputStream object based on the audio file data pointed to by the File.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  public AudioInputStream getAudioInputStream(File file)
    throws UnsupportedAudioFileException, IOException
  {
    InputStream inputStream = new FileInputStream(file);
    try {
      return getAudioInputStream(inputStream, (int) file.length());
    }
    catch (UnsupportedAudioFileException e) {
      inputStream.close();
      throw e;
    }
    catch (IOException e) {
      inputStream.close();
      throw e;
    }
  }

  /**
   * Obtains an audio input stream from the URL provided.
   * The URL must point to valid audio file data.
   * @param url the URL for which the AudioInputStream should be constructed.
   * @return an AudioInputStream object based on the audio file data pointed to by the URL.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  public AudioInputStream getAudioInputStream(URL url)
    throws UnsupportedAudioFileException, IOException
  {
    InputStream inputStream = url.openStream();
    try {
      return getAudioInputStream(inputStream);
    }
    catch (UnsupportedAudioFileException e) {
      inputStream.close();
      throw e;
    }
    catch (IOException e) {
      inputStream.close();
      throw e;
    }
  }

  /**
   * Obtains an audio input stream from the input stream provided.
   * The stream must point to valid audio file data.
   * @param stream the input stream from which the AudioInputStream should be constructed.
   * @return an AudioInputStream object based on the audio file data contained in the input stream.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  public AudioInputStream getAudioInputStream(InputStream stream)
    throws UnsupportedAudioFileException, IOException
  {
    return getAudioInputStream(stream, AudioSystem.NOT_SPECIFIED);
  }

  /**
   * Obtains an audio input stream from the input stream provided.
   * The stream must point to valid audio file data.
   * @param stream the input stream from which the AudioInputStream should be constructed.
   * @param medialength
   * @return an AudioInputStream object based on the audio file data contained in the input stream.
   * @exception UnsupportedAudioFileException if the File does not point to
   * a valid audio file data recognized by the system.
   * @exception IOException if an I/O exception occurs.
   */
  protected AudioInputStream getAudioInputStream(InputStream inputStream, int medialength)
    throws UnsupportedAudioFileException, IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
    AudioFileFormat audioFileFormat = getAudioFileFormat(inputStream, baos, medialength);
    SequenceInputStream sequenceInputStream = new SequenceInputStream(new ByteArrayInputStream(baos.toByteArray()), inputStream);
    return new AudioInputStream(sequenceInputStream, audioFileFormat.getFormat(), audioFileFormat.getFrameLength());
  }

  /**
   * Converts the bytes from the given array to an integer.
   * @param a - the array
   * @param i - the offset
   * @return the integer value of the reassembled bytes.
   */
  private static int bytestoint(byte[] a, int i)
  {
    return ((a[i+3] & 0xFF) << 24) | ((a[i+2] & 0xFF) << 16) | ((a[i+1] & 0xFF) << 8) | (a[i] & 0xFF);
  }
}
