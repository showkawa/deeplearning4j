<!DOCTYPE html>

<!--~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  ~
  ~
  ~ This program and the accompanying materials are made available under the
  ~ terms of the Apache License, Version 2.0 which is available at
  ~ https://www.apache.org/licenses/LICENSE-2.0.
  ~
  ~  See the NOTICE file distributed with this work for additional
  ~  information regarding copyright ownership.
  ~
  ~  Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  ~ WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  ~ License for the specific language governing permissions and limitations
  ~ under the License.
  ~
  ~ SPDX-License-Identifier: Apache-2.0
  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~-->


<html lang="en">
    <head>

        <meta charset="utf-8">
        <title>${train\.pagetitle}</title>
        <meta name="viewport" content="width=device-width, initial-scale=1">

        <link rel="stylesheet" href="/assets/webjars/coreui__coreui/2.1.9/dist/css/coreui.min.css">
        <link rel="stylesheet" href="/assets/css/style.css">


        <script src="/assets/webjars/jquery/3.4.1/dist/jquery.min.js"></script>
        <script src="/assets/webjars/popper.js/1.12.9/dist/umd/popper.min.js"></script>
        <script src="/assets/webjars/bootstrap/4.3.1/dist/js/bootstrap.min.js"></script>
        <script src="/assets/webjars/coreui__coreui/2.1.9/dist/js/coreui.min.js"></script>


        <!-- Icons -->
        <link rel="stylesheet" href="/assets/webjars/coreui__icons/0.3.0/css/coreui-icons.min.css"></script>


        <link rel="shortcut icon" href="/assets/img/favicon.ico">
    </head>

    <body class="app sidebar-show aside-menu-show">
        <header class="app-header navbar">
                <a class="header-text" href="#"><span>${train\.pagetitle}</span></a>
                <div id="sessionSelectDiv" style="display:none; float:right;">
                        <div style="color:white;">${train\.session\.label}</div>
                        <select id="sessionSelect" onchange='selectNewSession()'>
                        <option>(Session ID)</option>
                </select>
                </div>
                <div id="workerSelectDiv" style="display:none; float:right">
                        <div style="color:white;">${train\.session\.worker\.label}</div>
                        <select id="workerSelect" onchange='selectNewWorker()'>
                        <option>(Worker ID)</option>
                </select>
                </div>
        </header>
        <div class="app-body">
            <div class="sidebar">
                <nav class="sidebar-nav">
                    <ul class="nav">
                        <li class="nav-item"><a class="nav-link" href="overview"><i class="nav-icon cui-chart"></i>${train\.nav\.overview}</a></li>
                        <li class="nav-item"><a class="nav-link" href="model"><i class="nav-icon cui-graph"></i>${train\.nav\.model}</a></li>
                        <li class="nav-item"><a class="nav-link" href="system"><i class="nav-icon cui-speedometer"></i>${train\.nav\.system}</a></li>
                        <li class="nav-item nav-dropdown">
                            <a class="nav-link nav-dropdown-toggle" href="#">
                                <i class="nav-icon cui-globe"></i> ${train\.nav\.language}
                            </a>
                            <ul class="nav-dropdown-items">
                                <li class="nav-item"><a class="nav-link" href="javascript:void(0);" onclick="languageSelect('en', 'overview')"><i class="icon-file-alt"></i>English</a></li>
                                <li class="nav-item"><a class="nav-link" href="javascript:void(0);" onclick="languageSelect('de', 'overview')"><i class="icon-file-alt"></i>Deutsch</a></li>
                                <li class="nav-item"><a class="nav-link" href="javascript:void(0);" onclick="languageSelect('ja', 'overview')"><i class="icon-file-alt"></i>日本語</a></li>
                                <li class="nav-item"><a class="nav-link" href="javascript:void(0);" onclick="languageSelect('zh', 'overview')"><i class="icon-file-alt"></i>中文</a></li>
                                <li class="nav-item"><a class="nav-link" href="javascript:void(0);" onclick="languageSelect('ko', 'overview')"><i class="icon-file-alt"></i>한글</a></li>
                                <li class="nav-item"><a class="nav-link" href="javascript:void(0);" onclick="languageSelect('ru', 'overview')"><i class="icon-file-alt"></i>русский</a></li>
                            </ul>
                        </li>
                    </ul>

                </nav>
            </div>
            <main id="content" class="main">
                <div class="row">

                    <div class="col-8 chart-box">
                        <div class="chart-header">
                            <h2><b>${train\.overview\.chart\.scoreTitle}</b></h2>
                        </div>
                        <div>
                            <div id="scoreiterchart" class="center" style="height: 300px;" ></div>
                            <p id="hoverdata"><b>${train\.overview\.chart\.scoreTitleShort}
                                :</b> <span id="y">0</span>, <b>${train\.overview\.charts\.iteration}
                                :</b> <span id="x">
                                0</span></p>
                        </div>
                    </div>
                        <!-- End Score Chart-->
                        <!-- Start Model Table-->
                    <div class="col-4 chart-box">
                        <div class="chart-header">
                            <h2><b>${train\.overview\.perftable\.title}</b></h2>
                        </div>
                        <div>
                            <table class="table table-bordered table-striped table-condensed">
                                <tr>
                                    <td>${train\.overview\.modeltable\.modeltype}</td>
                                    <td id="modelType">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.modeltable\.nLayers}</td>
                                    <td id="nLayers">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.modeltable\.nParams}</td>
                                    <td id="nParams">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.perftable\.startTime}</td>
                                    <td id="startTime">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.perftable\.totalRuntime}</td>
                                    <td id="totalRuntime">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.perftable\.lastUpdate}</td>
                                    <td id="lastUpdate">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.perftable\.totalParamUpdates}</td>
                                    <td id="totalParamUpdates">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.perftable\.updatesPerSec}</td>
                                    <td id="updatesPerSec">Loading...</td>
                                </tr>
                                <tr>
                                    <td>${train\.overview\.perftable\.examplesPerSec}</td>
                                    <td id="examplesPerSec">Loading...</td>
                                </tr>
                            </table>
                        </div>
                    </div>
                        <!--End Model Table -->
                </div>


                <div class="row">
                        <!--Start Ratio Table -->
                    <div class="col chart-box">
                        <div class="chart-header">
                            <h2><b>${train\.overview\.chart\.updateRatioTitle}: log<sub>10</sub></b></h2>
                        </div>
                        <div class="box-content">
                            <div id="updateRatioChart" class="center" style="height: 300px;" ></div>
                            <p id="hoverdata"><b>${train\.overview\.chart\.updateRatioTitleShort}
                                :</b> <span id="yRatio">0</span>, <b>log<sub>
                                10</sub> ${train\.overview\.chart\.updateRatioTitleShort}
                                :</b> <span id="yLogRatio">0</span>
                                , <b>${train\.overview\.charts\.iteration}:</b> <span id="xRatio">
                                    0</span></p>
                        </div>

                    </div>
                        <!--End Ratio Table -->
                        <!--Start Variance Table -->
                    <div class="col chart-box">
                        <div class="chart-header">
                            <h2><b>${train\.overview\.chart\.stdevTitle}: log<sub>10</sub></b></h2>
                            <ul class="nav tab-menu nav-tabs" style="position:absolute; margin-top: -30px; right: 22px;">
                                <li class="active" id="stdevActivations"><a href="javascript:void(0);" onclick="selectStdevChart('stdevActivations')">${train\.overview\.chart\.stdevBtn\.activations}</a></li>
                                <li id="stdevGradients"><a href="javascript:void(0);" onclick="selectStdevChart('stdevGradients')">${train\.overview\.chart\.stdevBtn\.gradients}</a></li>
                                <li id="stdevUpdates"><a href="javascript:void(0);" onclick="selectStdevChart('stdevUpdates')">${train\.overview\.chart\.stdevBtn\.updates}</a></li>
                            </ul>
                        </div>
                        <div class="box-content">
                            <div id="stdevChart" class="center" style="height: 300px;" ></div>
                            <p id="hoverdata"><b>${train\.overview\.chart\.stdevTitleShort}
                                :</b> <span id="yStdev">0</span>, <b>log<sub>
                                10</sub> ${train\.overview\.chart\.stdevTitleShort}
                                :</b> <span id="yLogStdev">0</span>
                                , <b>${train\.overview\.charts\.iteration}:</b> <span id="xStdev">
                                    0</span></p>
                        </div>
                    </div>
                        <!-- End Variance Table -->
                </div>
            </main>
        </div>

        <script src="/assets/webjars/fullcalendar/1.6.4/fullcalendar.min.js"></script>
        <script src="/assets/webjars/excanvas/3/excanvas.js"></script>
        <script src="/assets/webjars/retinajs/0.0.2/retina.js"></script>
        <script src="/assets/webjars/flot/0.8.3/jquery.flot.js"></script>
        <script src="/assets/webjars/flot/0.8.3/jquery.flot.pie.js"></script>
        <script src="/assets/webjars/flot/0.8.3/jquery.flot.stack.js"></script>
        <script src="/assets/webjars/flot/0.8.3/jquery.flot.resize.min.js"></script>
        <script src="/assets/webjars/flot/0.8.3/jquery.flot.selection.js"></script>

        <script src="/assets/js/train/overview.js"></script>    <!-- Charts and tables are generated here! -->
        <script src="/assets/js/train/train.js"></script>   <!-- Common (lang selection, etc) -->
        <script src="/assets/js/counter.js"></script>

        <!-- Execute once on page load -->
        <script>
                $(document).ready(function () {
                    renderOverviewPage(true);
                });
        </script>

        <!-- Execute periodically (every 2 sec) -->
        <script>
                setInterval(function () {
                    renderOverviewPage(false);
                }, 2000);
        </script>
    </body>
</html>
