import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location(
    "gathering_nodes", Path(__file__).parents[1] / "scripts/gathering_nodes.py")
NODES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NODES)


def node(x, z, resource, kind="NODE", y=64):
    return {"x": x, "y": y, "z": z, "angle": 0, "type": kind, "resource": resource, "level": 105}


def bundled(x, z, resource, territory=None):
    return {**node(x, z, resource), "territory": territory, "cluster_id": None}


class GatheringNodesTest(unittest.TestCase):
    def regenerate(self, api, old, territories=None):
        return NODES.regenerate(api, old, territories or {}, "2026-11-19T09:46:20Z", "v3.7.2")

    def test_names_plain_dernic_and_molten_nodes_as_the_mod_does(self):
        old = [bundled(0, 0, "DERNIC_FISH"), bundled(100, 0, "DERNIC_TREE"), bundled(60, 0, "OAK"),
               bundled(0, 100, "MOLTEN_EEL"), bundled(0, 140, "MOLTEN")]
        api = [node(100, 0, "DERNIC"), node(40, 0, "DERNIC"), node(0, 1, "DERNIC", "CORNER"),
               node(0, 110, "MOLTEN"), node(0, 125, "MOLTEN"), node(0, 100, "MOLTEN", "WALL"), node(5, 5, "OAK")]
        names = [each["resource"] for each in self.regenerate(api, old)["data"]]
        self.assertEqual(
            ["DERNIC_TREE", "DERNIC_FISH", "DERNIC_ORE", "MOLTEN_EEL", "MOLTEN", "MOLTEN", "OAK"], names)

    def test_leaves_out_the_stray_dernic_nodes_and_labels_territories(self):
        territories = {"Void Valley": {"location": {"start": [20, 0], "end": [10, 10]}}}
        old = [bundled(0, 0, "OAK", "Old Label")]
        api = [node(632, -1220, "DERNIC", y=22), node(0, 0, "OAK"), node(10, 5, "OAK"), node(20, 5, "OAK")]
        document = self.regenerate(api, old, territories)
        self.assertEqual([(0, "Old Label"), (10, "Void Valley"), (20, None)],
                         [(each["x"], each["territory"]) for each in document["data"]])
        self.assertEqual(3, document["meta"]["count"])
        self.assertEqual("v3.7.2", document["meta"]["apiVersion"])


if __name__ == "__main__":
    unittest.main()
