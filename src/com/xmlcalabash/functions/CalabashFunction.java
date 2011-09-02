package com.xmlcalabash.functions;

import net.sf.saxon.lib.ExtensionFunctionDefinition;

import com.xmlcalabash.core.XProcConfiguration;

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by Norman Walsh are Copyright (C) Mark Logic Corporation. All Rights Reserved.
//
// Contributor(s): Norman Walsh.
//

/**
 * Implementation of the XSLT system-property() function
 */

public abstract class CalabashFunction extends ExtensionFunctionDefinition {
    
    protected final XProcConfiguration config;

    public CalabashFunction(XProcConfiguration config) {
        this.config = config;
    }
    
}