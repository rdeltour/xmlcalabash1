<p:declare-step xmlns:t="http://xproc.org/ns/testsuite" xmlns:p="http://www.w3.org/ns/xproc" xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:err="http://www.w3.org/ns/xproc-error" version="1.0">
      <p:output port="result"/>
      <p:http-request>
        <p:input port="source">
          <p:inline>
            <c:request href="http://tests.xproc.org/service/fixed-xml"/>
          </p:inline>
        </p:input>
      </p:http-request>
      
    </p:declare-step>