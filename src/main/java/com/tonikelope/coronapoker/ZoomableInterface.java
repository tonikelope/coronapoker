/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author tonikelope
 */
public interface ZoomableInterface {

    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier);

}