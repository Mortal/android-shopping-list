<?php
function get_shopping_list() {
	$fp = fopen('shoppinglist.txt', 'r');
	$line_count = 0;
	$last_line = '';
	while (($line = fgets($fp)) !== false) {
		$line_count += 1;
		$last_line = $line;
	}
	$data = explode(' ', trim($last_line, "\r\n"), 2);
	$shopping_list = array();
	if ($data[1] !== '') {
		$l = explode(';', $data[1]);
		foreach ($l as $part) {
			$shopping_list[] = trim($part);
		}
	}
	return $shopping_list;
}

function handle_shopping_list_post() {
	$shopping_list = get_shopping_list();
	$delete = $_POST['delete'];
	$insert = $_POST['insert'];
	$data = array();
	$data['errors'] = array();
	if (isset($_POST['delete'])) {
		$i = array_search($delete, $shopping_list);
		if ($i === false) {
			$data['errors'][] = 'Slet: Findes ikke';
			return $data;
		}
		array_splice($shopping_list, $i, 1);
	} else if ($insert) {
		$shopping_list[] = trim($insert);
	}
	$fp = fopen('shoppinglist.txt', 'a');
	$datetime = date(DATE_ISO8601);
	$s = implode('; ', $shopping_list);
	fwrite($fp, "$datetime $s\n");
	fclose($fp);
	$data['success'] = true;
	return $data;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' and $_POST['form'] == 'shopping_list') {
	$shopping_list_data = handle_shopping_list_post();
}

header('Content-Type: application/json');
print(json_encode(array('shopping' => get_shopping_list())));
