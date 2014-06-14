/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.services;

import javax.xml.bind.JAXBContext;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Guice module for wiring a number of base SIRI services.
 * 
 * @author bdferris
 * 
 */
public class SiriServicesModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SchedulingService.class).to(SchedulingServiceImpl.class);
    bind(HttpClientService.class).to(HttpClientServiceImpl.class);
    bind(JAXBContext.class).toProvider(JAXBContextProvider.class).in(
        Singleton.class);
  }

  @Provides
  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null)
      return false;
    return this.getClass().equals(o.getClass());
  }
}
