/*
 * Copyright (c) 2025 take-tk01
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.geysermc.extension.connect.config;

public class VHostDomainEntry {
    private String address;
    private int port;
    private boolean bedrock;

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isBedrock() { return bedrock; }
    public void setBedrock(boolean bedrock) { this.bedrock = bedrock; }
}
