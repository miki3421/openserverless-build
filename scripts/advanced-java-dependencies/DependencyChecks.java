/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file for additional
 * information regarding copyright ownership. The ASF licenses this file
 * under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy
 * at http://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable
 * law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.text.StringSubstitutor;
import org.yaml.snakeyaml.Yaml;

/** Offline checks use synthetic values only. No service endpoint is contacted. */
class DependencyChecks {
    static int checks;
    static int baselineFailures;
    static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        boolean legacy = args.length == 1 && args[0].equals("legacy");
        Map<String, String> sample = Map.of("tenant", "synthetic-alpha", "value", "caffè ☕");
        for (ObjectMapper mapper : new ObjectMapper[]{new ObjectMapper(), new ObjectMapper(new YAMLFactory()), new ObjectMapper(new CBORFactory())}) {
            try {
                check("jackson_" + mapper.getFactory().getFormatName(), sample.equals(mapper.readValue(mapper.writeValueAsBytes(sample), Map.class)));
            } catch (NoSuchMethodError error) {
                if (!legacy || !mapper.getFactory().getFormatName().equals("CBOR")) throw error;
                // Retain this observed baseline linkage failure separately. It
                // must not prevent the independent negative/control witnesses.
                System.out.println("BASELINE_FAILURE jackson_CBOR " + error.getMessage());
                baselineFailures++;
            }
        }
        String expression = "${script:javascript:1+1}";
        String interpolated = new StringSubstitutor(org.apache.commons.text.lookup.StringLookupFactory.INSTANCE.interpolatorStringLookup()).replace(expression);
        check("commons_text_script_default", legacy ? interpolated.equals("2") : interpolated.equals(expression));
        check("commons_text_normal_substitution", "Hello synthetic".equals(new StringSubstitutor(Map.of("name", "synthetic")).replace("Hello ${name}")));
        boolean unsafeTypeRejected = false;
        try {
            // URL construction alone performs no I/O. New safe defaults reject
            // an arbitrary explicit Java type before construction.
            Object value = new Yaml().load("!!java.net.URL [https://synthetic.invalid]");
            if (legacy) check("yaml_legacy_type_witness", value instanceof java.net.URL);
        } catch (org.yaml.snakeyaml.error.YAMLException expected) { unsafeTypeRejected = true; }
        check("yaml_explicit_type_policy", legacy ? !unsafeTypeRejected : unsafeTypeRejected);
        check("yaml_normal_mapping", "alpha".equals(((Map<?, ?>) new Yaml().load("tenant: alpha")).get("tenant")));
        Struct proto = Struct.newBuilder().putFields("tenant", Value.newBuilder().setStringValue("alpha").build()).build();
        check("protobuf_roundtrip", proto.equals(Struct.parseFrom(proto.toByteArray())));
        boolean invalidProto = false;
        try { Struct.parseFrom(new byte[]{10, 127}); } catch (com.google.protobuf.InvalidProtocolBufferException expected) { invalidProto = true; }
        check("protobuf_truncated_input", invalidProto);
        byte[] raw = "synthetic-tenant-data-☕".repeat(40).getBytes(StandardCharsets.UTF_8);
        check("snappy_roundtrip", Arrays.equals(raw, org.xerial.snappy.Snappy.uncompress(org.xerial.snappy.Snappy.compress(raw))));
        var lz4 = net.jpountz.lz4.LZ4Factory.fastestInstance();
        byte[] compressed = lz4.fastCompressor().compress(raw);
        check("lz4_roundtrip", Arrays.equals(raw, lz4.fastDecompressor().decompress(compressed, raw.length)));
        // The supported AWS SDK core no longer depends on the legacy Ion
        // implementation. Do not add an unused replacement library to test it.
        if (legacy) {
            Class<?> ionBuilder = Class.forName("software.amazon.ion.system.IonSystemBuilder");
            Object builder = ionBuilder.getMethod("standard").invoke(null);
            Object ion = ionBuilder.getMethod("build").invoke(builder);
            Object parsed = Class.forName("software.amazon.ion.IonSystem").getMethod("singleValue", String.class).invoke(ion, "{tenant:\"alpha\"}");
            check("legacy_ion_parser", parsed.toString().contains("alpha"));
        } else {
            boolean legacyIonAbsent = false;
            try { Class.forName("software.amazon.ion.system.IonSystemBuilder"); }
            catch (ClassNotFoundException expected) { legacyIonAbsent = true; }
            check("updated_aws_sdk_without_legacy_ion", legacyIonAbsent && com.amazonaws.util.VersionInfoUtils.getVersion().equals("1.12.797"));
        }
        String kube = "apiVersion: v1\nkind: Config\ncurrent-context: synthetic\nclusters:\n- name: synthetic\n  cluster:\n    server: https://synthetic.invalid\ncontexts:\n- name: synthetic\n  context:\n    cluster: synthetic\n    namespace: synthetic-alpha\n    user: synthetic\nusers:\n- name: synthetic\n  user:\n    token: synthetic-only\n";
        Config cfg = Config.fromKubeconfig(kube);
        check("kubeconfig_yaml", cfg.getNamespace().equals("synthetic-alpha") && cfg.getMasterUrl().startsWith("https://synthetic.invalid"));
        var pod = new PodBuilder().withNewMetadata().withName("synthetic").endMetadata().build();
        check("kubernetes_pod_json", Serialization.asJson(pod).contains("synthetic"));
        check("logging_provider", org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext);
        org.slf4j.LoggerFactory.getLogger("synthetic").info("dependency-check");
        var channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.forAddress("127.0.0.1", 1).usePlaintext().build();
        channel.shutdownNow();
        check("grpc_channel_creation", channel.isShutdown());
        var context = io.netty.handler.ssl.SslContextBuilder.forClient().sslProvider(io.netty.handler.ssl.SslProvider.JDK).build();
        var engine = context.newEngine(io.netty.buffer.UnpooledByteBufAllocator.DEFAULT);
        check("netty_tls_provider", engine.getSupportedProtocols().length > 0);
        engine.closeOutbound();
        var curator = org.apache.curator.framework.CuratorFrameworkFactory.newClient("127.0.0.1:1", new org.apache.curator.retry.RetryOneTime(100));
        check("curator_not_started", curator.getState().name().equals("LATENT"));
        curator.close();
        System.out.println("CHECKS=" + checks + " BASELINE_FAILURES=" + baselineFailures + " MODE=" + (legacy ? "legacy" : "candidate"));
        if (baselineFailures != 0) System.exit(2);
    }
}
